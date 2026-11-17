package io.github.nodyssey.data.valuation

import io.github.plaza.core.AppClock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/** Currency precision used for user-entered amounts and displayed totals. */
enum class ValuationCurrency(val code: String, val fractionDigits: Int) {
    CNY("CNY", 2),
    USD("USD", 2),
    EUR("EUR", 2),
    GBP("GBP", 2),
    HKD("HKD", 2),
    JPY("JPY", 0),
    CAD("CAD", 2),
    AUD("AUD", 2),
    SGD("SGD", 2),
    CHF("CHF", 2),
    KRW("KRW", 0),
    TRY("TRY", 2),
    BRL("BRL", 2),
}

enum class FeePayer { BUYER, SELLER, SPLIT }

enum class RenewalCycle(val months: Int) { MONTH(1), QUARTER(3), HALF_YEAR(6), YEAR(12) }

data class VpsValuationInput(
    val currency: ValuationCurrency,
    val renewalPrice: String,
    val expiresOn: LocalDate,
    val cycle: RenewalCycle,
    val askingPrice: String = "",
    val transferFee: String = "0",
    /** Percentage, with up to two fraction digits. */
    val brokerPercent: String = "0",
    val feePayer: FeePayer = FeePayer.SPLIT,
)

data class VpsValuationResult(
    val currency: ValuationCurrency,
    val totalCycleDays: Int,
    val remainingDays: Int,
    val renewalMinor: Long,
    val remainingValueMinor: Long,
    val askingPriceMinor: Long,
    val transferFeeMinor: Long,
    val brokerFeeMinor: Long,
    val buyerTotalMinor: Long,
    val sellerReceivesMinor: Long,
    val cnyPerUnitMicros: Long?,
    val remainingValueCnyMinor: Long?,
)

sealed interface VpsValuationError {
    data object InvalidRenewalPrice : VpsValuationError
    data object InvalidAskingPrice : VpsValuationError
    data object InvalidTransferFee : VpsValuationError
    data object InvalidBrokerPercent : VpsValuationError
    data object InvalidExpiry : VpsValuationError
    data object InvalidExchangeRate : VpsValuationError
}

sealed interface VpsValuationOutcome {
    data class Success(val result: VpsValuationResult) : VpsValuationOutcome
    data class Failure(val error: VpsValuationError) : VpsValuationOutcome
}

/** Exact minor-unit arithmetic; no binary floating point enters the estimate. */
object VpsValuationEngine {
    const val MICROS_PER_UNIT = 1_000_000L
    private const val BASIS_POINTS_PER_PERCENT = 100L
    private const val BASIS_POINTS_PER_WHOLE = 10_000L
    private const val MAX_MINOR = 100_000_000_000_000L
    private val SHANGHAI = TimeZone.of("Asia/Shanghai")

    fun today(nowMillis: Long = AppClock.System.nowMillis()): LocalDate =
        Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(SHANGHAI).date

    fun calculate(
        input: VpsValuationInput,
        today: LocalDate = today(),
        cnyPerUnitMicros: Long? = null,
    ): VpsValuationOutcome {
        val renewal = parseMinor(input.renewalPrice, input.currency.fractionDigits)
            ?: return VpsValuationOutcome.Failure(VpsValuationError.InvalidRenewalPrice)
        if (renewal <= 0) return VpsValuationOutcome.Failure(VpsValuationError.InvalidRenewalPrice)
        val asking = parseMinor(input.askingPrice.ifBlank { "0" }, input.currency.fractionDigits)
            ?: return VpsValuationOutcome.Failure(VpsValuationError.InvalidAskingPrice)
        val transfer = parseMinor(input.transferFee, input.currency.fractionDigits)
            ?: return VpsValuationOutcome.Failure(VpsValuationError.InvalidTransferFee)
        val brokerBps = parseMinor(input.brokerPercent, 2)
            ?: return VpsValuationOutcome.Failure(VpsValuationError.InvalidBrokerPercent)
        if (brokerBps > 100_00L) return VpsValuationOutcome.Failure(VpsValuationError.InvalidBrokerPercent)
        if (input.expiresOn < today) return VpsValuationOutcome.Failure(VpsValuationError.InvalidExpiry)
        if (cnyPerUnitMicros != null && cnyPerUnitMicros <= 0) return VpsValuationOutcome.Failure(VpsValuationError.InvalidExchangeRate)

        val cycleStart = input.expiresOn.minus(DatePeriod(months = input.cycle.months))
        val cycleDays = cycleStart.daysUntil(input.expiresOn).coerceAtLeast(1)
        val remainingDays = today.daysUntil(input.expiresOn).coerceIn(0, cycleDays)
        val remaining = multiplyDivide(renewal, remainingDays.toLong(), cycleDays.toLong())
            ?: return VpsValuationOutcome.Failure(VpsValuationError.InvalidRenewalPrice)
        val broker = multiplyDivide(asking, brokerBps, BASIS_POINTS_PER_WHOLE)
            ?: return VpsValuationOutcome.Failure(VpsValuationError.InvalidBrokerPercent)
        val totalFees = safeAdd(transfer, broker) ?: return VpsValuationOutcome.Failure(VpsValuationError.InvalidTransferFee)
        val buyerTotal = when (input.feePayer) {
            FeePayer.BUYER -> safeAdd(asking, totalFees)
            FeePayer.SELLER -> asking
            FeePayer.SPLIT -> safeAdd(asking, totalFees / 2)
        } ?: return VpsValuationOutcome.Failure(VpsValuationError.InvalidAskingPrice)
        val sellerFees = when (input.feePayer) {
            FeePayer.BUYER -> 0L
            FeePayer.SELLER -> totalFees
            FeePayer.SPLIT -> totalFees - totalFees / 2
        }
        val sellerReceives = (asking - sellerFees).coerceAtLeast(0)
        val cnyMinor = cnyPerUnitMicros?.let { multiplyDivide(remaining, it, MICROS_PER_UNIT) }
        if (cnyPerUnitMicros != null && cnyMinor == null) return VpsValuationOutcome.Failure(VpsValuationError.InvalidExchangeRate)
        return VpsValuationOutcome.Success(
            VpsValuationResult(
                currency = input.currency,
                totalCycleDays = cycleDays,
                remainingDays = remainingDays,
                renewalMinor = renewal,
                remainingValueMinor = remaining,
                askingPriceMinor = asking,
                transferFeeMinor = transfer,
                brokerFeeMinor = broker,
                buyerTotalMinor = buyerTotal,
                sellerReceivesMinor = sellerReceives,
                cnyPerUnitMicros = cnyPerUnitMicros,
                remainingValueCnyMinor = cnyMinor,
            ),
        )
    }

    fun parseMinor(raw: String, fractionDigits: Int): Long? {
        if (fractionDigits !in 0..2) return null
        val text = raw.trim()
        if (text.isEmpty() || text.startsWith('-') || text.startsWith('+')) return null
        val pieces = text.split('.')
        if (pieces.size > 2 || pieces[0].isEmpty() || pieces.any { it.any { c -> !c.isDigit() } }) return null
        val fraction = pieces.getOrElse(1) { "" }
        if (fraction.length > fractionDigits) return null
        val scale = if (fractionDigits == 0) {
            1L
        } else if (fractionDigits == 1) {
            10L
        } else {
            100L
        }
        val whole = pieces[0].toLongOrNull() ?: return null
        val padded = fraction.padEnd(fractionDigits, '0').ifEmpty { "0" }.toLongOrNull() ?: return null
        if (whole > MAX_MINOR / scale) return null
        val minor = whole * scale + padded
        return minor.takeIf { it <= MAX_MINOR }
    }

    fun formatMinor(minor: Long, fractionDigits: Int): String {
        val scale = when (fractionDigits) {
            0 -> 1L
            1 -> 10L
            else -> 100L
        }
        val absolute = minor.toULong()
        val whole = absolute / scale.toULong()
        if (fractionDigits == 0) return whole.toString()
        val fraction = (absolute % scale.toULong()).toString().padStart(fractionDigits, '0')
        return "$whole.$fraction"
    }

    private fun multiplyDivide(value: Long, multiplier: Long, divisor: Long): Long? {
        require(value >= 0 && multiplier >= 0 && divisor > 0)
        val quotient = value / divisor
        val remainder = value % divisor
        val whole = safeMultiply(quotient, multiplier) ?: return null
        val product = safeMultiply(remainder, multiplier) ?: return null
        val fractional = product / divisor + if (product % divisor >= (divisor + 1) / 2) 1L else 0L
        return safeAdd(whole, fractional)
    }

    private fun safeMultiply(left: Long, right: Long): Long? =
        if (left != 0L && right > Long.MAX_VALUE / left) null else left * right

    private fun safeAdd(left: Long, right: Long): Long? =
        if (right > Long.MAX_VALUE - left) null else left + right
}
