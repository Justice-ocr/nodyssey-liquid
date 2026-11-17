package io.github.nodyssey.data.valuation

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.plaza.core.AppClock
import io.github.plaza.core.net.HttpRequest
import io.github.plaza.core.net.HttpTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
private data class CachedExchangeRates(
    val asOf: String,
    val fetchedAtMillis: Long,
    val cnyPerUnitMicros: Map<String, Long>,
)

/** Latest Frankfurter rates with a 24-hour persistent cache and per-currency manual overrides. */
class ExchangeRateRepository(
    private val http: HttpTransport,
    private val dataStore: DataStore<Preferences>,
    private val clock: AppClock,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun rate(currency: ValuationCurrency, forceRefresh: Boolean = false): ExchangeRateResult {
        if (currency == ValuationCurrency.CNY) return ExchangeRateResult(1_000_000L, ExchangeRateFreshness.FRESH, "CNY")
        val prefs = try {
            dataStore.data.first()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return ExchangeRateResult(null, ExchangeRateFreshness.UNAVAILABLE, null)
        }
        val manual = decodeLongMap(prefs[MANUAL_RATES])[currency.code]
        if (manual != null && manual > 0) return ExchangeRateResult(manual, ExchangeRateFreshness.MANUAL, "Manual")
        val cached = decodeCache(prefs[CACHE])
        if (!forceRefresh && cached != null && clock.nowMillis() - cached.fetchedAtMillis in 0..CACHE_TTL_MILLIS) {
            cached.cnyPerUnitMicros[currency.code]?.let {
                return ExchangeRateResult(it, ExchangeRateFreshness.FRESH, cached.asOf)
            }
        }
        val refreshed = refresh()
        if (refreshed != null) {
            refreshed.cnyPerUnitMicros[currency.code]?.let {
                return ExchangeRateResult(it, ExchangeRateFreshness.FRESH, refreshed.asOf)
            }
        }
        cached?.cnyPerUnitMicros?.get(currency.code)?.let {
            return ExchangeRateResult(it, ExchangeRateFreshness.STALE, cached.asOf)
        }
        return ExchangeRateResult(null, ExchangeRateFreshness.UNAVAILABLE, null)
    }

    suspend fun setManualRate(currency: ValuationCurrency, cnyPerUnit: String?) {
        require(currency != ValuationCurrency.CNY)
        val parsed = cnyPerUnit?.let(::parseMicros)
        require(cnyPerUnit == null || (parsed != null && parsed > 0))
        dataStore.edit { prefs ->
            val values = decodeLongMap(prefs[MANUAL_RATES]).toMutableMap()
            if (parsed == null) values.remove(currency.code) else values[currency.code] = parsed
            prefs[MANUAL_RATES] = json.encodeToString(values)
        }
    }

    private suspend fun refresh(): CachedExchangeRates? {
        val response = try {
            http.execute(
                HttpRequest(
                    url = "$BASE_URL/latest?base=CNY&symbols=$SYMBOLS",
                    headers = mapOf("Accept" to "application/json"),
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return null
        }
        if (!response.isSuccessful || !response.url.startsWith("$BASE_URL/")) return null
        val body = runCatching { json.parseToJsonElement(response.body).jsonObject }.getOrNull() ?: return null
        val asOf = body["date"]?.jsonPrimitive?.content?.takeIf { DATE_PATTERN.matches(it) } ?: return null
        val foreignPerCny = runCatching { body.getValue("rates").jsonObject }.getOrNull() ?: return null
        val rates = mutableMapOf(ValuationCurrency.CNY.code to 1_000_000L)
        ValuationCurrency.entries.filterNot { it == ValuationCurrency.CNY }.forEach { currency ->
            val quoted = foreignPerCny[currency.code]?.jsonPrimitive?.content?.let(::parseMicros) ?: return@forEach
            if (quoted > 0) rates[currency.code] = roundedDivide(1_000_000_000_000L, quoted)
        }
        val snapshot = CachedExchangeRates(asOf, clock.nowMillis(), rates)
        try {
            dataStore.edit { it[CACHE] = json.encodeToString(snapshot) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return snapshot
        }
        return snapshot
    }

    private fun decodeCache(value: String?): CachedExchangeRates? =
        value?.let { runCatching { json.decodeFromString<CachedExchangeRates>(it) }.getOrNull() }

    private fun decodeLongMap(value: String?): Map<String, Long> =
        value?.let { runCatching { json.decodeFromString<Map<String, Long>>(it) }.getOrNull() }.orEmpty()

    private fun parseMicros(raw: String): Long? {
        val parts = raw.split('.')
        if (parts.size > 2 || parts.firstOrNull().isNullOrEmpty() || parts.any { part -> part.any { !it.isDigit() } }) return null
        val whole = parts[0].toLongOrNull() ?: return null
        if (whole > Long.MAX_VALUE / 1_000_000L) return null
        val fraction = parts.getOrElse(1) { "" }
        if (fraction.length > 6) return null
        val fractional = fraction.padEnd(6, '0').toLongOrNull() ?: 0L
        val scaledWhole = whole * 1_000_000L
        if (fractional > Long.MAX_VALUE - scaledWhole) return null
        return scaledWhole + fractional
    }

    private fun roundedDivide(numerator: Long, denominator: Long): Long =
        numerator / denominator + if (numerator % denominator >= denominator / 2 + denominator % 2) 1 else 0

    companion object {
        const val CACHE_TTL_MILLIS = 24 * 60 * 60 * 1000L
        private const val BASE_URL = "https://api.frankfurter.dev/v1"
        private const val SYMBOLS = "USD,EUR,GBP,HKD,JPY,CAD,AUD,SGD,CHF,KRW,TRY,BRL"
        private val DATE_PATTERN = Regex("\\d{4}-\\d{2}-\\d{2}")
        private val CACHE = stringPreferencesKey("vps_exchange_rate_cache")
        private val MANUAL_RATES = stringPreferencesKey("vps_exchange_manual_rates")
    }
}

enum class ExchangeRateFreshness { FRESH, STALE, MANUAL, UNAVAILABLE }

data class ExchangeRateResult(
    val cnyPerUnitMicros: Long?,
    val freshness: ExchangeRateFreshness,
    val asOf: String?,
)
