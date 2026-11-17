package io.github.nodyssey.data.valuation

import io.github.nodyssey.data.testPreferenceStore
import io.github.plaza.core.AppClock
import io.github.plaza.core.net.HttpRequest
import io.github.plaza.core.net.HttpResponse
import io.github.plaza.core.net.HttpTransport
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ExchangeRateRepositoryTest {
    @Test
    fun `inverse rates are cached and stale values survive network failure`() = runTest {
        var now = 1_000_000L
        var calls = 0
        var fails = false
        val http = object : HttpTransport {
            override suspend fun execute(request: HttpRequest, onUploadProgress: ((Float) -> Unit)?): HttpResponse {
                calls++
                if (fails) error("offline")
                return HttpResponse(
                    code = 200,
                    url = request.url,
                    headers = emptyMap(),
                    body = """{"date":"2025-01-02","rates":{"USD":0.125}}""",
                )
            }
        }
        val repository = ExchangeRateRepository(
            http = http,
            dataStore = testPreferenceStore(backgroundScope, "vps-rates"),
            clock = AppClock { now },
        )

        val fresh = repository.rate(ValuationCurrency.USD)
        assertEquals(8_000_000L, fresh.cnyPerUnitMicros)
        assertEquals(ExchangeRateFreshness.FRESH, fresh.freshness)
        assertEquals(1, calls)

        assertEquals(fresh, repository.rate(ValuationCurrency.USD))
        assertEquals(1, calls)

        now += ExchangeRateRepository.CACHE_TTL_MILLIS + 1
        fails = true
        val stale = repository.rate(ValuationCurrency.USD)
        assertEquals(ExchangeRateFreshness.STALE, stale.freshness)
        assertEquals("2025-01-02", stale.asOf)
        assertEquals(2, calls)
    }

    @Test
    fun `manual rate overrides network and clearing it returns to cached rate`() = runTest {
        var calls = 0
        val repository = ExchangeRateRepository(
            http = object : HttpTransport {
                override suspend fun execute(request: HttpRequest, onUploadProgress: ((Float) -> Unit)?): HttpResponse {
                    calls++
                    return HttpResponse(200, request.url, emptyMap(), """{"date":"2025-02-03","rates":{"USD":0.2}}""")
                }
            },
            dataStore = testPreferenceStore(backgroundScope, "vps-manual-rates"),
            clock = AppClock { 2_000_000L },
        )

        repository.rate(ValuationCurrency.USD)
        repository.setManualRate(ValuationCurrency.USD, "7.25")
        assertEquals(ExchangeRateResult(7_250_000L, ExchangeRateFreshness.MANUAL, "Manual"), repository.rate(ValuationCurrency.USD))
        repository.setManualRate(ValuationCurrency.USD, null)
        assertEquals(5_000_000L, repository.rate(ValuationCurrency.USD).cnyPerUnitMicros)
        assertEquals(1, calls)
    }
}
