package app.coolapk.kmp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.coolapk.kmp.core.DiscoveryCard
import com.coolapk.kmp.core.DiscoveryPage
import com.coolapk.kmp.core.DiscoveryTab
import com.coolapk.kmp.core.HomeCursor
import com.coolapk.kmp.core.HomeRepository
import com.coolapk.kmp.core.createPlatformGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiscoverySectionState(
    val cards: List<DiscoveryCard> = emptyList(),
    val selectorHeader: List<DiscoveryCard> = emptyList(),
    val selectedFilterUrl: String? = null,
    val loading: Boolean = false,
    val page: Int = 0,
    val cursor: HomeCursor? = null,
    val pageContext: String? = null,
    val hasMore: Boolean = true,
    val error: String? = null,
)

data class DiscoveryUiState(
    val configLoading: Boolean = false,
    val configError: String? = null,
    val tabs: List<DiscoveryTab> = emptyList(),
    val selectedTab: DiscoveryTab? = null,
    val sections: Map<String, DiscoverySectionState> = emptyMap(),
) {
    val section: DiscoverySectionState get() = sections[selectedTab?.key] ?: DiscoverySectionState()
    val cards: List<DiscoveryCard> get() = section.cards
    val selectedFilterUrl: String? get() = section.selectedFilterUrl
    val loading: Boolean get() = section.loading
    val page: Int get() = section.page
    val cursor: HomeCursor? get() = section.cursor
    val pageContext: String? get() = section.pageContext
    val hasMore: Boolean get() = section.hasMore
    val error: String? get() = section.error ?: if (tabs.isEmpty()) configError else null
}

class DiscoveryViewModel(private val repositoryProvider: () -> HomeRepository) : ViewModel() {
    private val _state = MutableStateFlow(DiscoveryUiState())
    val state: StateFlow<DiscoveryUiState> = _state.asStateFlow()
    private val repository by lazy(repositoryProvider)
    private val pageRequests = mutableMapOf<String, Int>()
    private val scrollPositions = mutableMapOf<String, Pair<Int, Int>>()
    private var configRequest = 0
    private var configRequested = false

    fun ensureLoaded() {
        if (!configRequested) loadConfig()
    }

    fun loadConfig() {
        configRequested = true
        val request = ++configRequest
        _state.update { it.copy(configLoading = true, configError = null) }
        viewModelScope.launch {
            try {
                val config = repository.loadDiscoveryConfig()
                if (request != configRequest) return@launch
                val previous = _state.value
                val oldTabs = previous.tabs.associateBy(DiscoveryTab::key)
                val newTabs = config.tabs.associateBy(DiscoveryTab::key)
                val sections = previous.sections.filterKeys { key ->
                    oldTabs[key]?.url == newTabs[key]?.url && key in newTabs
                }
                val selected = config.tabs.firstOrNull {
                    it.key == previous.selectedTab?.key && !it.isWebTab()
                } ?: config.tabs.firstOrNull {
                    it.key == config.selectedKey && !it.isWebTab()
                } ?: config.tabs.firstOrNull { !it.isWebTab() }
                _state.update {
                    it.copy(configLoading = false, configError = null, tabs = config.tabs,
                        selectedTab = selected, sections = sections)
                }
                if (selected != null && sections[selected.key]?.let { it.page > 0 || it.loading } != true) {
                    refresh()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (request != configRequest) return@launch
                _state.update {
                    it.copy(configLoading = false,
                        configError = failure.message ?: "发现栏目加载失败")
                }
            }
        }
    }

    fun selectTab(tab: DiscoveryTab) {
        val current = _state.value.tabs.firstOrNull { it.key == tab.key } ?: return
        if (current.isWebTab() || _state.value.selectedTab?.key == current.key) return
        _state.update { it.copy(selectedTab = current) }
        val section = _state.value.section
        if (section.page == 0 && !section.loading && section.error == null) refresh()
    }

    fun selectFilter(card: DiscoveryCard) {
        val tab = _state.value.selectedTab ?: return
        if (!card.entityType.equals("selectorLink", true)) return
        val target = card.url?.trim()?.takeIf(::isDiscoveryFilterUrl) ?: return
        val section = _state.value.section
        if (section.selectedFilterUrl == target && section.error == null) return
        val header = section.selectorHeader.ifEmpty { section.cards.selectorHeader() }
        saveScrollPosition(tab.key, 0, 0)
        updateSection(tab.key) {
            DiscoverySectionState(
                cards = header,
                selectorHeader = header,
                selectedFilterUrl = target,
            )
        }
        loadFirstPage(tab)
    }

    fun refresh() {
        val tab = _state.value.selectedTab ?: run { loadConfig(); return }
        if (tab.isWebTab()) return
        val section = _state.value.section
        val header = if (section.selectedFilterUrl != null) section.selectorHeader else emptyList()
        updateSection(tab.key) {
            it.copy(cards = if (header.isNotEmpty()) header else it.cards,
                page = 0, cursor = null, pageContext = null, hasMore = true, error = null)
        }
        loadFirstPage(tab)
    }

    fun loadMore() = loadMore(retry = false)

    fun retry() {
        if (_state.value.page == 0) refresh() else loadMore(retry = true)
    }

    fun saveScrollPosition(tabKey: String, index: Int, offset: Int) {
        scrollPositions[tabKey] = index.coerceAtLeast(0) to offset.coerceAtLeast(0)
    }

    fun scrollPosition(tabKey: String): Pair<Int, Int> = scrollPositions[tabKey] ?: (0 to 0)

    private fun loadFirstPage(tab: DiscoveryTab) {
        val section = _state.value.sections[tab.key] ?: DiscoverySectionState()
        val request = nextPageRequest(tab.key)
        updateSection(tab.key) { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val result = repository.loadDiscoverySection(tab.requestTab(section.selectedFilterUrl))
                if (!isCurrentRequest(tab, request)) return@launch
                updateSection(tab.key) { current ->
                    val header = current.selectorHeader
                    val cards = if (current.selectedFilterUrl != null) {
                        (header + result.cards).distinctBy(DiscoveryCard::id)
                    } else {
                        result.cards.distinctBy(DiscoveryCard::id)
                    }
                    current.copy(cards = cards,
                        selectorHeader = if (current.selectedFilterUrl != null) header else cards.selectorHeader(),
                        loading = false, page = 1, cursor = result.cursor(),
                        pageContext = result.pageContext, hasMore = result.hasMore,
                        error = null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (!isCurrentRequest(tab, request)) return@launch
                updateSection(tab.key) {
                    it.copy(loading = false, error = failure.message ?: "栏目加载失败")
                }
            }
        }
    }

    private fun loadMore(retry: Boolean) {
        val tab = _state.value.selectedTab ?: return
        val section = _state.value.section
        if (section.loading || section.page == 0 || !section.hasMore || section.error != null && !retry) return
        val request = nextPageRequest(tab.key)
        updateSection(tab.key) { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val result = repository.loadDiscoverySection(
                    tab.requestTab(section.selectedFilterUrl), section.page + 1,
                    section.cursor, section.pageContext,
                )
                if (!isCurrentRequest(tab, request)) return@launch
                updateSection(tab.key) { current ->
                    val existing = current.cards.map(DiscoveryCard::id).toSet()
                    val fresh = result.cards.filterNot { it.id in existing }
                    current.copy(cards = current.cards + fresh, loading = false,
                        page = section.page + 1, cursor = result.cursor() ?: section.cursor,
                        pageContext = result.pageContext ?: section.pageContext,
                        hasMore = result.hasMore && fresh.isNotEmpty(), error = null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (!isCurrentRequest(tab, request)) return@launch
                updateSection(tab.key) {
                    it.copy(loading = false, error = failure.message ?: "加载更多失败")
                }
            }
        }
    }

    private fun updateSection(key: String, transform: (DiscoverySectionState) -> DiscoverySectionState) {
        _state.update { state ->
            state.copy(sections = state.sections + (key to transform(state.sections[key] ?: DiscoverySectionState())))
        }
    }

    private fun nextPageRequest(key: String): Int = (pageRequests[key] ?: 0).plus(1).also {
        pageRequests[key] = it
    }

    private fun isCurrentRequest(tab: DiscoveryTab, request: Int): Boolean =
        pageRequests[tab.key] == request && _state.value.tabs.any {
            it.key == tab.key && it.url == tab.url
        }

    private fun DiscoveryTab.requestTab(filterUrl: String?): DiscoveryTab =
        if (filterUrl == null) this else copy(url = filterUrl)

    private fun DiscoveryTab.isWebTab(): Boolean =
        webUrl?.startsWith("http", ignoreCase = true) == true ||
            url.startsWith("http", ignoreCase = true)

    private fun List<DiscoveryCard>.selectorHeader(): List<DiscoveryCard> {
        val lastSelector = indexOfLast { it.entityType.equals("selectorLink", true) }
        return if (lastSelector >= 0) take(lastSelector + 1) else emptyList()
    }

    private fun DiscoveryPage.cursor(): HomeCursor? =
        firstItem?.let { first -> lastItem?.let { last -> HomeCursor(first, last) } }

    companion object {
        val Factory = viewModelFactory {
            initializer { DiscoveryViewModel { HomeRepository(createPlatformGateway()) } }
        }
    }
}

internal fun isDiscoveryFilterUrl(target: String): Boolean {
    if (target.isBlank() || target.length > 2048 || target.any { it.code < 0x20 || it.code == 0x7F }) return false
    val lower = target.lowercase()
    return !listOf("http:", "https:", "javascript:", "file:", "data:").any(lower::startsWith)
}
