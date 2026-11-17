package io.github.nodyssey.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.TitleAlertRule
import io.github.nodyssey.data.TitleKeywordStore
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.model.TitleKeywordKind
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TitleKeywordsViewModel(
    private val kind: TitleKeywordKind,
    private val store: TitleKeywordStore,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val form = MutableStateFlow(TitleKeywordForm())
    private var categorySaveJob: Job? = null
    private var excludedSaveJob: Job? = null

    val uiState: StateFlow<TitleKeywordsUiState> =
        combine(store.alertRules, store.keywords(kind), form, settingsRepository.settings) { rules, keywords, typed, settings ->
            TitleKeywordsUiState(
                kind = kind,
                keywords = keywords,
                alertRules = rules,
                input = typed.input,
                isRegex = typed.isRegex,
                invalidPattern = typed.isRegex && typed.input.isNotBlank() && TitleKeywordStore.normalizePattern(typed.input) == null,
                categorySlugs = typed.categorySlugs ?: settings.alertCategorySlugs.joinToString(", "),
                excludedWords = typed.excludedWords ?: settings.alertExcludedWords.joinToString(", "),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TitleKeywordsUiState(kind = kind),
        )

    fun onInputChange(value: String) = form.update {
        it.copy(input = value.take(if (it.isRegex) TitleKeywordStore.MAX_PATTERN_LENGTH else TitleKeywordStore.MAX_KEYWORD_LENGTH))
    }

    fun setRegex(enabled: Boolean) = form.update {
        it.copy(
            isRegex = enabled,
            input = if (enabled) it.input else it.input.take(TitleKeywordStore.MAX_KEYWORD_LENGTH),
        )
    }

    fun add() {
        val typed = form.value
        if (typed.input.isBlank()) return
        viewModelScope.launch {
            val added = if (kind == TitleKeywordKind.ALERT) {
                store.addAlertRule(typed.input, typed.isRegex)
            } else {
                store.add(kind, typed.input)
            }
            if (added) form.update { it.copy(input = "") }
        }
    }

    fun remove(keyword: String) {
        viewModelScope.launch { store.remove(kind, keyword) }
    }

    fun removeAlertRule(rule: TitleAlertRule) {
        viewModelScope.launch { store.removeAlertRule(rule) }
    }

    fun setCategorySlugs(value: String) {
        form.update { it.copy(categorySlugs = value) }
        categorySaveJob?.cancel()
        categorySaveJob = viewModelScope.launch {
            delay(FILTER_SAVE_DELAY_MILLIS)
            settingsRepository.setAlertCategorySlugs(value.split(','))
            val saved = settingsRepository.settings.first().alertCategorySlugs.joinToString(", ")
            form.update { it.copy(categorySlugs = saved) }
        }
    }

    fun setExcludedWords(value: String) {
        form.update { it.copy(excludedWords = value) }
        excludedSaveJob?.cancel()
        excludedSaveJob = viewModelScope.launch {
            delay(FILTER_SAVE_DELAY_MILLIS)
            settingsRepository.setAlertExcludedWords(value.split(','))
            val saved = settingsRepository.settings.first().alertExcludedWords.joinToString(", ")
            form.update { it.copy(excludedWords = saved) }
        }
    }

    companion object {
        const val FILTER_SAVE_DELAY_MILLIS = 400L

        fun factory(
            container: AppContainer,
            kind: TitleKeywordKind,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { TitleKeywordsViewModel(kind, container.titleKeywordStore, container.settingsRepository) }
            }
    }
}

private data class TitleKeywordForm(
    val input: String = "",
    val isRegex: Boolean = false,
    val categorySlugs: String? = null,
    val excludedWords: String? = null,
)

data class TitleKeywordsUiState(
    val kind: TitleKeywordKind,
    val keywords: List<String> = emptyList(),
    val alertRules: List<TitleAlertRule> = emptyList(),
    val input: String = "",
    val isRegex: Boolean = false,
    val invalidPattern: Boolean = false,
    val categorySlugs: String = "",
    val excludedWords: String = "",
)
