package io.github.nodyssey.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.data.TitleAlertRule
import io.github.nodyssey.model.TitleKeywordKind
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.keywords_add_action
import io.github.nodyssey.ui.resources.keywords_add_placeholder
import io.github.nodyssey.ui.resources.keywords_alert_categories
import io.github.nodyssey.ui.resources.keywords_alert_categories_hint
import io.github.nodyssey.ui.resources.keywords_alert_exclusions
import io.github.nodyssey.ui.resources.keywords_alert_exclusions_hint
import io.github.nodyssey.ui.resources.keywords_alert_footer
import io.github.nodyssey.ui.resources.keywords_alert_title
import io.github.nodyssey.ui.resources.keywords_block_footer
import io.github.nodyssey.ui.resources.keywords_block_title
import io.github.nodyssey.ui.resources.keywords_empty
import io.github.nodyssey.ui.resources.keywords_regex_description
import io.github.nodyssey.ui.resources.keywords_regex_invalid
import io.github.nodyssey.ui.resources.keywords_regex_placeholder
import io.github.nodyssey.ui.resources.keywords_regex_toggle
import io.github.nodyssey.ui.resources.keywords_remove
import io.github.nodyssey.ui.resources.keywords_rule_regex
import io.github.nodyssey.ui.resources.keywords_section_rules
import io.github.nodyssey.ui.resources.keywords_section_scope
import io.github.plaza.designsys.component.GroupedListItem
import io.github.plaza.designsys.component.GroupedRow
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.PlazaFieldDefaults
import io.github.plaza.designsys.component.SectionLabel
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.stringResource

@Composable
fun TitleKeywordsRoute(
    viewModel: TitleKeywordsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TitleKeywordsScreen(
        state = state,
        onBack = onBack,
        onInputChange = viewModel::onInputChange,
        onAdd = viewModel::add,
        onRemove = viewModel::remove,
        onRemoveAlertRule = viewModel::removeAlertRule,
        onRegexChange = viewModel::setRegex,
        onCategorySlugsChange = viewModel::setCategorySlugs,
        onExcludedWordsChange = viewModel::setExcludedWords,
        modifier = modifier,
    )
}

/** The field to add a word, the words, and one line on what they do and where they live. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitleKeywordsScreen(
    state: TitleKeywordsUiState,
    onBack: () -> Unit,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onRemoveAlertRule: (TitleAlertRule) -> Unit,
    onRegexChange: (Boolean) -> Unit,
    onCategorySlugsChange: (String) -> Unit,
    onExcludedWordsChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appBarState = rememberOneHandAppBarState()
    val alerts = state.kind == TitleKeywordKind.ALERT
    Scaffold(
        modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
        topBar = {
            OneHandTopAppBar(
                title = stringResource(if (alerts) Res.string.keywords_alert_title else Res.string.keywords_block_title),
                state = appBarState,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .readableWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LayerPageGutter, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            OutlinedTextField(
                value = state.input,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(if (alerts && state.isRegex) Res.string.keywords_regex_placeholder else Res.string.keywords_add_placeholder)) },
                shape = PlazaFieldDefaults.shape,
                colors = PlazaFieldDefaults.colors(),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAdd() }),
                trailingIcon = {
                    TextButton(onClick = onAdd, enabled = state.input.isNotBlank() && !state.invalidPattern) {
                        Text(stringResource(Res.string.keywords_add_action))
                    }
                },
                supportingText = if (state.invalidPattern) {
                    { Text(stringResource(Res.string.keywords_regex_invalid), color = MaterialTheme.colorScheme.error) }
                } else {
                    null
                },
            )
            if (alerts) {
                SectionLabel(stringResource(Res.string.keywords_section_scope))
                GroupedRow(
                    title = stringResource(Res.string.keywords_regex_toggle),
                    subtitle = stringResource(Res.string.keywords_regex_description),
                    first = true,
                    last = true,
                    checked = state.isRegex,
                    onCheckedChange = onRegexChange,
                )
                OutlinedTextField(
                    value = state.categorySlugs,
                    onValueChange = onCategorySlugsChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(Res.string.keywords_alert_categories)) },
                    placeholder = { Text(stringResource(Res.string.keywords_alert_categories_hint)) },
                    shape = PlazaFieldDefaults.shape,
                    colors = PlazaFieldDefaults.colors(),
                )
                OutlinedTextField(
                    value = state.excludedWords,
                    onValueChange = onExcludedWordsChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(Res.string.keywords_alert_exclusions)) },
                    placeholder = { Text(stringResource(Res.string.keywords_alert_exclusions_hint)) },
                    shape = PlazaFieldDefaults.shape,
                    colors = PlazaFieldDefaults.colors(),
                )
            }
            SectionLabel(stringResource(Res.string.keywords_section_rules))
            val displayedRules = if (alerts) state.alertRules else state.keywords.map { TitleAlertRule(it, false) }
            if (displayedRules.isEmpty()) {
                Text(
                    stringResource(Res.string.keywords_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.xs),
                )
            } else {
                Column {
                    displayedRules.forEachIndexed { index, rule ->
                        GroupedListItem(
                            first = index == 0,
                            last = index == displayedRules.lastIndex,
                            headlineContent = {
                                Text(
                                    if (rule.isRegex) "${rule.text} · ${stringResource(Res.string.keywords_rule_regex)}" else rule.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = {
                                    if (alerts) onRemoveAlertRule(rule) else onRemove(rule.text)
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.keywords_remove, rule.text))
                                }
                            },
                        )
                    }
                }
            }
            Text(
                stringResource(if (alerts) Res.string.keywords_alert_footer else Res.string.keywords_block_footer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.xs),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun TitleKeywordsPreview() {
    PlazaTheme {
        TitleKeywordsScreen(
            state = TitleKeywordsUiState(kind = TitleKeywordKind.BLOCK, keywords = listOf("这是关键词预览")),
            onBack = {},
            onInputChange = {},
            onAdd = {},
            onRemove = {},
            onRemoveAlertRule = {},
            onRegexChange = {},
            onCategorySlugsChange = {},
            onExcludedWordsChange = {},
        )
    }
}
