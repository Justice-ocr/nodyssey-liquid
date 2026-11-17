package io.github.nodyssey.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.valuation.ExchangeRateFreshness
import io.github.nodyssey.data.valuation.ExchangeRateRepository
import io.github.nodyssey.data.valuation.ExchangeRateResult
import io.github.nodyssey.data.valuation.FeePayer
import io.github.nodyssey.data.valuation.RenewalCycle
import io.github.nodyssey.data.valuation.ValuationCurrency
import io.github.nodyssey.data.valuation.VpsValuationEngine
import io.github.nodyssey.data.valuation.VpsValuationError
import io.github.nodyssey.data.valuation.VpsValuationInput
import io.github.nodyssey.data.valuation.VpsValuationOutcome
import io.github.nodyssey.data.valuation.VpsValuationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

class VpsValuationViewModel(
    private val rates: ExchangeRateRepository,
) : ViewModel() {
    private val today = VpsValuationEngine.today()
    private var rateRequestId = 0L
    private val _state = MutableStateFlow(
        VpsValuationUiState(
            expiresOn = today.plus(DatePeriod(months = 1)).toString(),
        ),
    )
    val state: StateFlow<VpsValuationUiState> = _state.asStateFlow()

    init {
        refreshRate()
    }

    fun setCurrency(currency: ValuationCurrency) {
        _state.update {
            it.copy(
                currency = currency,
                manualRateText = "",
                exchangeRateMicros = if (currency == ValuationCurrency.CNY) 1_000_000L else null,
                rateFreshness = null,
            )
        }
        refreshRate()
    }

    fun setCycle(cycle: RenewalCycle) = update { copy(cycle = cycle) }
    fun setRenewalPrice(value: String) = update { copy(renewalPrice = value) }
    fun setExpiry(value: String) = update { copy(expiresOn = value) }
    fun setAskingPrice(value: String) = update { copy(askingPrice = value) }
    fun setTransferFee(value: String) = update { copy(transferFee = value) }
    fun setBrokerPercent(value: String) = update { copy(brokerPercent = value) }
    fun setFeePayer(value: FeePayer) = update { copy(feePayer = value) }
    fun setManualRateText(value: String) = update { copy(manualRateText = value) }

    fun refreshRate(forceRefresh: Boolean = false) {
        val currency = _state.value.currency
        val requestId = ++rateRequestId
        if (currency == ValuationCurrency.CNY) {
            _state.update { it.copy(exchangeRateMicros = 1_000_000L, rateFreshness = ExchangeRateFreshness.FRESH, rateAsOf = "CNY") }
            recalculate()
            return
        }
        viewModelScope.launch {
            val result = safeRate(currency, forceRefresh)
            if (requestId != rateRequestId || _state.value.currency != currency) return@launch
            _state.update {
                it.copy(
                    exchangeRateMicros = result.cnyPerUnitMicros,
                    rateFreshness = result.freshness,
                    rateAsOf = result.asOf,
                    manualRateText = if (result.freshness == ExchangeRateFreshness.MANUAL) formatRate(result.cnyPerUnitMicros) else it.manualRateText,
                )
            }
            recalculate()
        }
    }

    fun forceRefreshRate() = refreshRate(forceRefresh = true)

    fun useManualRate() {
        val currency = _state.value.currency
        if (currency == ValuationCurrency.CNY) return
        val requestId = ++rateRequestId
        viewModelScope.launch {
            val text = _state.value.manualRateText.trim()
            try {
                rates.setManualRate(currency, text)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (requestId == rateRequestId && _state.value.currency == currency) {
                    _state.update { it.copy(result = null, error = VpsValuationError.InvalidExchangeRate) }
                }
                return@launch
            }
            val result = safeRate(currency)
            if (requestId != rateRequestId || _state.value.currency != currency) return@launch
            _state.update {
                it.copy(
                    exchangeRateMicros = result.cnyPerUnitMicros,
                    rateFreshness = result.freshness,
                    rateAsOf = result.asOf,
                    manualRateText = if (result.freshness == ExchangeRateFreshness.MANUAL) formatRate(result.cnyPerUnitMicros) else it.manualRateText,
                )
            }
            recalculate()
        }
    }

    fun clearManualRate() {
        val currency = _state.value.currency
        if (currency == ValuationCurrency.CNY) return
        val requestId = ++rateRequestId
        viewModelScope.launch {
            try {
                rates.setManualRate(currency, null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (requestId == rateRequestId && _state.value.currency == currency) {
                    _state.update { it.copy(result = null, error = VpsValuationError.InvalidExchangeRate) }
                }
                return@launch
            }
            if (requestId == rateRequestId && _state.value.currency == currency) refreshRate()
        }
    }

    private suspend fun safeRate(currency: ValuationCurrency, forceRefresh: Boolean = false): ExchangeRateResult =
        try {
            rates.rate(currency, forceRefresh)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ExchangeRateResult(null, ExchangeRateFreshness.UNAVAILABLE, null)
        }

    private fun update(change: VpsValuationUiState.() -> VpsValuationUiState) {
        _state.update(change)
        recalculate()
    }

    private fun recalculate() {
        val current = _state.value
        val expiry = runCatching { LocalDate.parse(current.expiresOn.trim()) }.getOrNull()
        if (expiry == null) {
            _state.update { it.copy(result = null, error = VpsValuationError.InvalidExpiry) }
            return
        }
        val input = VpsValuationInput(
            currency = current.currency,
            renewalPrice = current.renewalPrice,
            expiresOn = expiry,
            cycle = current.cycle,
            askingPrice = current.askingPrice,
            transferFee = current.transferFee,
            brokerPercent = current.brokerPercent,
            feePayer = current.feePayer,
        )
        when (val outcome = VpsValuationEngine.calculate(input, today, current.exchangeRateMicros)) {
            is VpsValuationOutcome.Success -> _state.update { it.copy(result = outcome.result, error = null) }
            is VpsValuationOutcome.Failure -> _state.update { it.copy(result = null, error = outcome.error) }
        }
    }
}

data class VpsValuationUiState(
    val currency: ValuationCurrency = ValuationCurrency.CNY,
    val renewalPrice: String = "10",
    val expiresOn: String,
    val cycle: RenewalCycle = RenewalCycle.MONTH,
    val askingPrice: String = "",
    val transferFee: String = "0",
    val brokerPercent: String = "0",
    val feePayer: FeePayer = FeePayer.SPLIT,
    val manualRateText: String = "",
    val exchangeRateMicros: Long? = 1_000_000L,
    val rateFreshness: ExchangeRateFreshness? = null,
    val rateAsOf: String? = null,
    val result: VpsValuationResult? = null,
    val error: VpsValuationError? = null,
)

private fun formatRate(micros: Long?): String {
    if (micros == null) return ""
    val whole = micros / VpsValuationEngine.MICROS_PER_UNIT
    val fraction = (micros % VpsValuationEngine.MICROS_PER_UNIT).toString().padStart(6, '0').trimEnd('0')
    return if (fraction.isEmpty()) whole.toString() else "$whole.$fraction"
}

fun vpsValuationViewModelFactory(rates: ExchangeRateRepository): ViewModelProvider.Factory =
    viewModelFactory {
        initializer { VpsValuationViewModel(rates) }
    }
