package io.github.nodyssey.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.data.valuation.ExchangeRateFreshness
import io.github.nodyssey.data.valuation.FeePayer
import io.github.nodyssey.data.valuation.RenewalCycle
import io.github.nodyssey.data.valuation.ValuationCurrency
import io.github.nodyssey.data.valuation.VpsValuationEngine
import io.github.nodyssey.data.valuation.VpsValuationResult
import io.github.nodyssey.ui.common.ToolSectionCard
import io.github.nodyssey.ui.common.ToolTopBar
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_refresh
import io.github.nodyssey.ui.resources.vps_ask_price
import io.github.nodyssey.ui.resources.vps_broker_percent
import io.github.nodyssey.ui.resources.vps_buyer_total
import io.github.nodyssey.ui.resources.vps_clear_manual_rate
import io.github.nodyssey.ui.resources.vps_contract
import io.github.nodyssey.ui.resources.vps_currency
import io.github.nodyssey.ui.resources.vps_cycle
import io.github.nodyssey.ui.resources.vps_cycle_half_year
import io.github.nodyssey.ui.resources.vps_cycle_month
import io.github.nodyssey.ui.resources.vps_cycle_quarter
import io.github.nodyssey.ui.resources.vps_cycle_year
import io.github.nodyssey.ui.resources.vps_expires_on
import io.github.nodyssey.ui.resources.vps_fee_payer
import io.github.nodyssey.ui.resources.vps_fee_payer_buyer
import io.github.nodyssey.ui.resources.vps_fee_payer_seller
import io.github.nodyssey.ui.resources.vps_fee_payer_split
import io.github.nodyssey.ui.resources.vps_insert_markdown
import io.github.nodyssey.ui.resources.vps_invalid_input
import io.github.nodyssey.ui.resources.vps_manual_rate
import io.github.nodyssey.ui.resources.vps_markdown_cny
import io.github.nodyssey.ui.resources.vps_markdown_deal
import io.github.nodyssey.ui.resources.vps_markdown_summary
import io.github.nodyssey.ui.resources.vps_rate_fresh
import io.github.nodyssey.ui.resources.vps_rate_manual
import io.github.nodyssey.ui.resources.vps_rate_stale
import io.github.nodyssey.ui.resources.vps_rate_unavailable
import io.github.nodyssey.ui.resources.vps_remaining_days
import io.github.nodyssey.ui.resources.vps_remaining_value
import io.github.nodyssey.ui.resources.vps_renewal_price
import io.github.nodyssey.ui.resources.vps_seller_receives
import io.github.nodyssey.ui.resources.vps_title
import io.github.nodyssey.ui.resources.vps_transaction
import io.github.nodyssey.ui.resources.vps_transfer_fee
import io.github.plaza.designsys.component.LayerCard
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.PlazaFieldDefaults
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.stringResource

@Composable
fun VpsValuationRoute(
    viewModel: VpsValuationViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onInsertMarkdown: ((String) -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    VpsValuationScreen(
        state = state,
        onBack = onBack,
        onCurrency = viewModel::setCurrency,
        onCycle = viewModel::setCycle,
        onRenewalPrice = viewModel::setRenewalPrice,
        onExpiry = viewModel::setExpiry,
        onAskingPrice = viewModel::setAskingPrice,
        onTransferFee = viewModel::setTransferFee,
        onBrokerPercent = viewModel::setBrokerPercent,
        onFeePayer = viewModel::setFeePayer,
        onManualRate = viewModel::setManualRateText,
        onApplyManualRate = viewModel::useManualRate,
        onClearManualRate = viewModel::clearManualRate,
        onRefreshRate = viewModel::forceRefreshRate,
        onInsertMarkdown = onInsertMarkdown,
        modifier = modifier,
    )
}

@Composable
fun VpsValuationDialog(
    viewModel: VpsValuationViewModel,
    onDismiss: () -> Unit,
    onInsertMarkdown: (String) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        VpsValuationRoute(
            viewModel = viewModel,
            onBack = onDismiss,
            onInsertMarkdown = onInsertMarkdown,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun VpsValuationScreen(
    state: VpsValuationUiState,
    onBack: () -> Unit,
    onCurrency: (ValuationCurrency) -> Unit,
    onCycle: (RenewalCycle) -> Unit,
    onRenewalPrice: (String) -> Unit,
    onExpiry: (String) -> Unit,
    onAskingPrice: (String) -> Unit,
    onTransferFee: (String) -> Unit,
    onBrokerPercent: (String) -> Unit,
    onFeePayer: (FeePayer) -> Unit,
    onManualRate: (String) -> Unit,
    onApplyManualRate: () -> Unit,
    onClearManualRate: () -> Unit,
    onRefreshRate: () -> Unit,
    modifier: Modifier = Modifier,
    onInsertMarkdown: ((String) -> Unit)?,
) {
    Scaffold(
        modifier = modifier,
        topBar = { ToolTopBar(stringResource(Res.string.vps_title), onBack) },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().readableWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = LayerPageGutter, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            ValuationSummary(state, onInsertMarkdown)
            ToolSectionCard(stringResource(Res.string.vps_contract), PlazaIcons.EventAvailable) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    CurrencyPicker(state.currency, onCurrency)
                    CyclePicker(state.cycle, onCycle)
                }
                DecimalField(state.renewalPrice, onRenewalPrice, stringResource(Res.string.vps_renewal_price))
                OutlinedTextField(
                    value = state.expiresOn,
                    onValueChange = onExpiry,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.vps_expires_on)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
            }
            ToolSectionCard(stringResource(Res.string.vps_transaction), PlazaIcons.Wallet) {
                DecimalField(state.askingPrice, onAskingPrice, stringResource(Res.string.vps_ask_price))
                DecimalField(state.transferFee, onTransferFee, stringResource(Res.string.vps_transfer_fee))
                DecimalField(state.brokerPercent, onBrokerPercent, stringResource(Res.string.vps_broker_percent))
                FeePayerPicker(state.feePayer, onFeePayer)
            }
            if (state.currency != ValuationCurrency.CNY) {
                ToolSectionCard(stringResource(Res.string.vps_manual_rate), PlazaIcons.Tune) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(rateStatus(state), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = onRefreshRate) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(Res.string.action_refresh))
                        }
                    }
                    OutlinedTextField(
                        value = state.manualRateText,
                        onValueChange = onManualRate,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("1 ${state.currency.code} = CNY") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        TextButton(onClick = onApplyManualRate) { Text(stringResource(Res.string.vps_rate_manual)) }
                        TextButton(onClick = onClearManualRate) { Text(stringResource(Res.string.vps_clear_manual_rate)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ValuationSummary(state: VpsValuationUiState, onInsertMarkdown: ((String) -> Unit)?) {
    LayerCard(containerColor = MaterialTheme.colorScheme.primaryContainer, contentPadding = PaddingValues(Spacing.lg)) {
        Text(stringResource(Res.string.vps_remaining_value), style = MaterialTheme.typography.labelLarge)
        val result = state.result
        if (result == null) {
            Text("—", style = MaterialTheme.typography.headlineLarge)
            if (state.error != null) Text(stringResource(Res.string.vps_invalid_input), style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(money(result.remainingValueMinor, result.currency), style = MaterialTheme.typography.headlineLarge)
            if (result.currency != ValuationCurrency.CNY) {
                result.remainingValueCnyMinor?.let {
                    Text(stringResource(Res.string.vps_markdown_cny, VpsValuationEngine.formatMinor(it, 2)), style = MaterialTheme.typography.bodyMedium)
                }
            }
            LinearProgressIndicator(progress = { result.remainingDays.toFloat() / result.totalCycleDays }, modifier = Modifier.fillMaxWidth())
            Text(stringResource(Res.string.vps_remaining_days, result.remainingDays, result.totalCycleDays), style = MaterialTheme.typography.labelLarge)
            if (result.askingPriceMinor > 0) {
                Text(stringResource(Res.string.vps_buyer_total, money(result.buyerTotalMinor, result.currency)))
                Text(stringResource(Res.string.vps_seller_receives, money(result.sellerReceivesMinor, result.currency)))
            }
            if (onInsertMarkdown != null) {
                val markdown = valuationMarkdown(result)
                Button(onClick = { onInsertMarkdown(markdown) }) { Text(stringResource(Res.string.vps_insert_markdown)) }
            }
        }
    }
}

@Composable
private fun DecimalField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        shape = PlazaFieldDefaults.shape,
        colors = PlazaFieldDefaults.colors(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

@Composable
private fun CurrencyPicker(value: ValuationCurrency, onChange: (ValuationCurrency) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }) { Text("${stringResource(Res.string.vps_currency)} · ${value.code}") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ValuationCurrency.entries.forEach { currency ->
                DropdownMenuItem(text = { Text(currency.code) }, onClick = {
                    expanded = false
                    onChange(currency)
                })
            }
        }
    }
}

@Composable
private fun CyclePicker(value: RenewalCycle, onChange: (RenewalCycle) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }) { Text(stringResource(Res.string.vps_cycle) + " · " + cycleName(value)) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RenewalCycle.entries.forEach { cycle ->
                DropdownMenuItem(text = { Text(cycleName(cycle)) }, onClick = {
                    expanded = false
                    onChange(cycle)
                })
            }
        }
    }
}

@Composable
private fun FeePayerPicker(value: FeePayer, onChange: (FeePayer) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }) { Text(stringResource(Res.string.vps_fee_payer) + " · " + payerName(value)) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            FeePayer.entries.forEach { payer ->
                DropdownMenuItem(text = { Text(payerName(payer)) }, onClick = {
                    expanded = false
                    onChange(payer)
                })
            }
        }
    }
}

@Composable
private fun cycleName(cycle: RenewalCycle): String = stringResource(
    when (cycle) {
        RenewalCycle.MONTH -> Res.string.vps_cycle_month
        RenewalCycle.QUARTER -> Res.string.vps_cycle_quarter
        RenewalCycle.HALF_YEAR -> Res.string.vps_cycle_half_year
        RenewalCycle.YEAR -> Res.string.vps_cycle_year
    },
)

@Composable
private fun payerName(payer: FeePayer): String = stringResource(
    when (payer) {
        FeePayer.BUYER -> Res.string.vps_fee_payer_buyer
        FeePayer.SELLER -> Res.string.vps_fee_payer_seller
        FeePayer.SPLIT -> Res.string.vps_fee_payer_split
    },
)

@Composable
private fun rateStatus(state: VpsValuationUiState): String = when (state.rateFreshness) {
    ExchangeRateFreshness.FRESH -> stringResource(Res.string.vps_rate_fresh, state.rateAsOf.orEmpty(), rateValue(state.exchangeRateMicros))
    ExchangeRateFreshness.STALE -> stringResource(Res.string.vps_rate_stale, state.rateAsOf.orEmpty(), rateValue(state.exchangeRateMicros))
    ExchangeRateFreshness.MANUAL -> stringResource(Res.string.vps_rate_manual) + " · " + rateValue(state.exchangeRateMicros)
    ExchangeRateFreshness.UNAVAILABLE, null -> stringResource(Res.string.vps_rate_unavailable)
}

private fun rateValue(micros: Long?): String {
    if (micros == null) return "—"
    val whole = micros / VpsValuationEngine.MICROS_PER_UNIT
    val fraction = (micros % VpsValuationEngine.MICROS_PER_UNIT).toString().padStart(6, '0').trimEnd('0')
    return if (fraction.isEmpty()) whole.toString() else "$whole.$fraction"
}

private fun money(minor: Long, currency: ValuationCurrency) =
    "${currency.code} ${VpsValuationEngine.formatMinor(minor, currency.fractionDigits)}"

@Composable
private fun valuationMarkdown(result: VpsValuationResult): String {
    val cny = result.remainingValueCnyMinor?.let { amount ->
        stringResource(Res.string.vps_markdown_cny, VpsValuationEngine.formatMinor(amount, 2))
    }.orEmpty()
    val lines = mutableListOf(
        stringResource(
            Res.string.vps_markdown_summary,
            result.currency.code,
            VpsValuationEngine.formatMinor(result.remainingValueMinor, result.currency.fractionDigits),
            cny,
            result.remainingDays,
            result.totalCycleDays,
        ),
    )
    if (result.askingPriceMinor > 0) {
        lines += stringResource(
            Res.string.vps_markdown_deal,
            money(result.askingPriceMinor, result.currency),
            money(result.buyerTotalMinor, result.currency),
            money(result.sellerReceivesMinor, result.currency),
        )
    }
    return lines.joinToString("\n")
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun VpsValuationPreview() {
    PlazaTheme {
        VpsValuationScreen(
            state = VpsValuationUiState(expiresOn = "2026-12-17"),
            onBack = {}, onCurrency = {}, onCycle = {}, onRenewalPrice = {}, onExpiry = {},
            onAskingPrice = {}, onTransferFee = {}, onBrokerPercent = {}, onFeePayer = {},
            onManualRate = {}, onApplyManualRate = {}, onClearManualRate = {}, onRefreshRate = {}, onInsertMarkdown = null,
        )
    }
}
