package io.github.nodyssey.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.data.Board
import io.github.nodyssey.data.TitleAlertRule
import io.github.nodyssey.model.TitleKeywordKind
import io.github.nodyssey.ui.common.ToolSectionCard
import io.github.nodyssey.ui.common.ToolTopBar
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_cancel
import io.github.nodyssey.ui.resources.action_done
import io.github.nodyssey.ui.resources.keywords_add_action
import io.github.nodyssey.ui.resources.keywords_add_placeholder
import io.github.nodyssey.ui.resources.keywords_alert_categories
import io.github.nodyssey.ui.resources.keywords_alert_category_unavailable
import io.github.nodyssey.ui.resources.keywords_alert_exclusions
import io.github.nodyssey.ui.resources.keywords_alert_exclusions_hint
import io.github.nodyssey.ui.resources.keywords_alert_footer
import io.github.nodyssey.ui.resources.keywords_block_footer
import io.github.nodyssey.ui.resources.keywords_block_title
import io.github.nodyssey.ui.resources.keywords_empty
import io.github.nodyssey.ui.resources.keywords_regex_invalid
import io.github.nodyssey.ui.resources.keywords_regex_placeholder
import io.github.nodyssey.ui.resources.keywords_regex_toggle
import io.github.nodyssey.ui.resources.keywords_remove
import io.github.nodyssey.ui.resources.keywords_rule_regex
import io.github.nodyssey.ui.resources.keywords_section_rules
import io.github.nodyssey.ui.resources.keywords_section_scope
import io.github.nodyssey.ui.resources.radar_add_rule
import io.github.nodyssey.ui.resources.radar_count
import io.github.nodyssey.ui.resources.radar_plain
import io.github.nodyssey.ui.resources.radar_title
import io.github.nodyssey.ui.resources.search_all_boards
import io.github.plaza.designsys.component.GroupedListItem
import io.github.plaza.designsys.component.LayerCard
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.PlazaFieldDefaults
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.SectionLabel
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
    onCategorySlugsChange: (List<String>) -> Unit,
    onExcludedWordsChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val alerts = state.kind == TitleKeywordKind.ALERT
    val displayedRules = if (alerts) state.alertRules else state.keywords.map { TitleAlertRule(it, false) }
    Scaffold(
        modifier = modifier,
        topBar = { ToolTopBar(stringResource(if (alerts) Res.string.radar_title else Res.string.keywords_block_title), onBack) },
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
            LayerCard(containerColor = MaterialTheme.colorScheme.primaryContainer, contentPadding = PaddingValues(Spacing.lg)) {
                Text(stringResource(Res.string.keywords_section_rules), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(Res.string.radar_count, displayedRules.size), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(if (alerts) Res.string.keywords_alert_footer else Res.string.keywords_block_footer), style = MaterialTheme.typography.bodySmall)
            }
            ToolSectionCard(stringResource(Res.string.radar_add_rule), Icons.Default.Search) {
                if (alerts) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        FilterChip(selected = !state.isRegex, onClick = { onRegexChange(false) }, label = { Text(stringResource(Res.string.radar_plain)) })
                        FilterChip(selected = state.isRegex, onClick = { onRegexChange(true) }, label = { Text(stringResource(Res.string.keywords_regex_toggle)) })
                    }
                }
                OutlinedTextField(
                    value = state.input,
                    isError = state.invalidPattern,
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
            }
            if (alerts) {
                ToolSectionCard(stringResource(Res.string.keywords_section_scope), PlazaIcons.Tune) {
                    RadarBoardPicker(
                        boards = state.boards,
                        selectedSlugs = state.categorySlugs,
                        onApply = onCategorySlugsChange,
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
            }
            SectionLabel(stringResource(Res.string.keywords_section_rules))
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
                            leadingContent = { Icon(if (rule.isRegex) PlazaIcons.Code else Icons.Default.Search, null) },
                            supportingContent = { Text(stringResource(if (rule.isRegex) Res.string.keywords_rule_regex else Res.string.radar_plain)) },
                            headlineContent = {
                                Text(
                                    rule.text,
                                    maxLines = 2,
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
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RadarBoardPicker(
    boards: List<Board>,
    selectedSlugs: List<String>,
    onApply: (List<String>) -> Unit,
) {
    val titles = remember(boards) { boards.mapNotNull { board -> board.slug?.let { it to board.title } }.toMap() }
    val title = stringResource(Res.string.keywords_alert_categories)
    val allBoards = stringResource(Res.string.search_all_boards)
    var open by rememberSaveable { mutableStateOf(false) }

    ListItem(
        onClick = { open = true },
        modifier = Modifier.fillMaxWidth(),
        enabled = titles.isNotEmpty() || selectedSlugs.isNotEmpty(),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        trailingContent = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
        supportingContent = {
            Text(
                text = if (selectedSlugs.isEmpty()) allBoards else selectedSlugs.joinToString(", ") { titles[it] ?: it },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
    ) { Text(title) }

    if (open) {
        var draft by rememberSaveable(stateSaver = listSaver(save = { it }, restore = { it })) { mutableStateOf(selectedSlugs) }
        // A saved pick may no longer be in the catalog. Keep it visible so it can be removed explicitly.
        val options = (titles.keys + selectedSlugs + draft).toList()
        val optionColors = ListItemDefaults.colors(containerColor = Color.Transparent)
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    item {
                        ListItem(
                            selected = draft.isEmpty(),
                            onClick = { draft = emptyList() },
                            colors = optionColors,
                            leadingContent = { RadioButton(selected = draft.isEmpty(), onClick = null) },
                        ) { Text(allBoards) }
                    }
                    items(options, key = { it }) { slug ->
                        val checked = slug in draft
                        ListItem(
                            checked = checked,
                            onCheckedChange = { draft = if (it) draft + slug else draft - slug },
                            colors = optionColors,
                            leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                        ) { Text(titles[slug] ?: stringResource(Res.string.keywords_alert_category_unavailable, slug)) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    open = false
                    if (draft != selectedSlugs) onApply(draft)
                }) { Text(stringResource(Res.string.action_done)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
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
