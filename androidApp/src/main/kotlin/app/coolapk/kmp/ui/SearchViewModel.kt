package app.coolapk.kmp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.coolapk.kmp.core.HomeCursor
import com.coolapk.kmp.core.HomeRepository
import com.coolapk.kmp.core.SearchCategory
import com.coolapk.kmp.core.SearchResult
import com.coolapk.kmp.core.createPlatformGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchTabUiState(
    val items: List<SearchResult> = emptyList(),
    val page: Int = 0,
    val cursor: HomeCursor? = null,
    val hasMore: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null,
)

data class SearchUiState(
    val query: String = "",
    val category: SearchCategory = SearchCategory.ALL,
    val askFeedType: String = "all",
    val tabs: Map<SearchCategory, SearchTabUiState> = emptyMap(),
    val hotWords: List<String> = emptyList(),
    val hotLoading: Boolean = false,
    val hotError: String? = null,
) {
    val activeTab: SearchTabUiState get() = tabs[category] ?: SearchTabUiState()
}

class SearchViewModel(private val repositoryProvider: () -> HomeRepository) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private val repository by lazy { runCatching(repositoryProvider).getOrNull() }
    private var generation = 0
    private var hotRequest = 0
    private val tabRequests = mutableMapOf<SearchCategory, Int>()

    init { loadHotWords() }

    fun search(value: String) {
        val query = value.trim()
        if (query.isEmpty()) return
        val changed = _state.value.query != query
        if (changed) {
            generation++
            _state.update { it.copy(query = query, tabs = emptyMap()) }
        }
        loadFirstPage(force = changed)
    }

    fun selectCategory(category: SearchCategory) {
        if (_state.value.category == category) return
        _state.update { it.copy(category = category) }
        if (_state.value.query.isNotBlank() && _state.value.tabs[category]?.page == null) loadFirstPage()
    }

    fun selectAskFeedType(value: String) {
        if (value !in setOf("all", "question", "answer") || _state.value.askFeedType == value) return
        generation++
        _state.update { it.copy(askFeedType = value, tabs = it.tabs - SearchCategory.ASK) }
        if (_state.value.category == SearchCategory.ASK && _state.value.query.isNotBlank()) loadFirstPage()
    }

    fun retry() = loadFirstPage(force = true)

    fun loadMore() {
        val snapshot = _state.value
        val tab = snapshot.activeTab
        if (snapshot.query.isBlank() || tab.loading || tab.page == 0 || !tab.hasMore) return
        loadPage(snapshot.category, tab.page + 1, append = true)
    }

    fun loadHotWords(refresh: Boolean = false) {
        if (_state.value.hotLoading) return
        val request = ++hotRequest
        _state.update { it.copy(hotLoading = true, hotError = null) }
        viewModelScope.launch {
            runCatching { repository?.loadHotSearchWords(refresh) ?: error("数据服务尚未就绪") }
                .onSuccess { words ->
                    if (request == hotRequest) _state.update {
                        it.copy(hotWords = words, hotLoading = false)
                    }
                }
                .onFailure { error ->
                    if (request == hotRequest) _state.update {
                        it.copy(hotLoading = false, hotError = error.message ?: "热门搜索加载失败")
                    }
                }
        }
    }

    private fun loadFirstPage(force: Boolean = false) {
        val snapshot = _state.value
        if (snapshot.query.isBlank() || snapshot.activeTab.loading) return
        if (!force && snapshot.activeTab.page > 0) return
        loadPage(snapshot.category, page = 1, append = false)
    }

    private fun loadPage(category: SearchCategory, page: Int, append: Boolean) {
        val snapshot = _state.value
        val query = snapshot.query
        val askFeedType = snapshot.askFeedType
        val previous = snapshot.tabs[category] ?: SearchTabUiState()
        val request = (tabRequests[category] ?: 0) + 1
        tabRequests[category] = request
        val currentGeneration = generation
        _state.update { state ->
            state.copy(tabs = state.tabs + (category to previous.copy(loading = true, error = null)))
        }
        viewModelScope.launch {
            runCatching {
                repository?.searchByType(query, category, page, if (append) previous.cursor else null, askFeedType)
                    ?: error("数据服务尚未就绪")
            }.onSuccess { result ->
                if (currentGeneration != generation || request != tabRequests[category]) return@onSuccess
                _state.update { state ->
                    val existing = if (append) state.tabs[category]?.items.orEmpty() else emptyList()
                    val existingIds = existing.mapTo(mutableSetOf()) { "${it.kind}:${it.id}" }
                    val newItems = result.items.filter { existingIds.add("${it.kind}:${it.id}") }
                    state.copy(tabs = state.tabs + (category to SearchTabUiState(
                        items = existing + newItems,
                        page = page,
                        cursor = result.firstItem?.let { first ->
                            result.lastItem?.let { last -> HomeCursor(first, last) }
                        },
                        hasMore = result.hasMore && (!append || newItems.isNotEmpty()),
                    )))
                }
            }.onFailure { error ->
                if (currentGeneration != generation || request != tabRequests[category]) return@onFailure
                _state.update { state ->
                    val tab = state.tabs[category] ?: SearchTabUiState()
                    state.copy(tabs = state.tabs + (category to tab.copy(
                        loading = false, error = error.message ?: "搜索失败",
                    )))
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { SearchViewModel { HomeRepository(createPlatformGateway()) } }
        }
    }
}
