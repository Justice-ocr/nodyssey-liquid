package io.github.nodyssey.data.valuation

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class VpsValuationEngineTest {
    private fun input(
        currency: ValuationCurrency = ValuationCurrency.USD,
        renewal: String = "100",
        expires: String = "2024-03-31",
        cycle: RenewalCycle = RenewalCycle.MONTH,
        asking: String = "",
        transfer: String = "0",
        broker: String = "0",
        payer: FeePayer = FeePayer.SPLIT,
    ) = VpsValuationInput(
        currency = currency,
        renewalPrice = renewal,
        expiresOn = LocalDate.parse(expires),
        cycle = cycle,
        askingPrice = asking,
        transferFee = transfer,
        brokerPercent = broker,
        feePayer = payer,
    )

    @Test
    fun `calendar month across leap day is prorated and rounded half up`() {
        val outcome = VpsValuationEngine.calculate(input(), LocalDate.parse("2024-03-16"))
        val result = assertIs<VpsValuationOutcome.Success>(outcome).result

        assertEquals(31, result.totalCycleDays)
        assertEquals(15, result.remainingDays)
        assertEquals(4_839L, result.remainingValueMinor)
    }

    @Test
    fun `zero digit currencies round to whole units`() {
        val result = assertIs<VpsValuationOutcome.Success>(
            VpsValuationEngine.calculate(
                input(currency = ValuationCurrency.JPY, renewal = "101"),
                LocalDate.parse("2024-03-16"),
            ),
        ).result

        assertEquals(49L, result.remainingValueMinor)
    }

    @Test
    fun `fee split conserves every minor unit`() {
        val result = assertIs<VpsValuationOutcome.Success>(
            VpsValuationEngine.calculate(
                input(asking = "100", transfer = "10", broker = "2.5", payer = FeePayer.SPLIT),
                LocalDate.parse("2024-03-16"),
            ),
        ).result

        assertEquals(250L, result.brokerFeeMinor)
        assertEquals(10_625L, result.buyerTotalMinor)
        assertEquals(9_375L, result.sellerReceivesMinor)
    }

    @Test
    fun `conversion uses integer minor units and half up`() {
        val result = assertIs<VpsValuationOutcome.Success>(
            VpsValuationEngine.calculate(
                input(),
                LocalDate.parse("2024-03-16"),
                cnyPerUnitMicros = 7_250_000L,
            ),
        ).result

        assertEquals(35_083L, result.remainingValueCnyMinor)
    }

    @Test
    fun `expiry on today returns zero remaining value`() {
        val result = assertIs<VpsValuationOutcome.Success>(
            VpsValuationEngine.calculate(input(expires = "2024-03-16"), LocalDate.parse("2024-03-16")),
        ).result

        assertEquals(0, result.remainingDays)
        assertEquals(0L, result.remainingValueMinor)
    }

    @Test
    fun `an exchange conversion overflow is reported without crashing`() {
        val outcome = VpsValuationEngine.calculate(
            input(),
            LocalDate.parse("2024-03-16"),
            cnyPerUnitMicros = Long.MAX_VALUE,
        )

        assertEquals(VpsValuationError.InvalidExchangeRate, (outcome as VpsValuationOutcome.Failure).error)
    }

    @Test
    fun `invalid amounts and broker percentages are rejected`() {
        assertEquals(
            VpsValuationError.InvalidRenewalPrice,
            (VpsValuationEngine.calculate(input(renewal = "-1"), LocalDate.parse("2024-03-16")) as VpsValuationOutcome.Failure).error,
        )
        assertEquals(
            VpsValuationError.InvalidBrokerPercent,
            (VpsValuationEngine.calculate(input(broker = "100.01"), LocalDate.parse("2024-03-16")) as VpsValuationOutcome.Failure).error,
        )
    }
}
