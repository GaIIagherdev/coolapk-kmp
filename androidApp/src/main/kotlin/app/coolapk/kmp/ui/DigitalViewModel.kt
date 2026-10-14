package app.coolapk.kmp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.coolapk.kmp.core.DigitalEntity
import com.coolapk.kmp.core.DigitalPage
import com.coolapk.kmp.core.DigitalTab
import com.coolapk.kmp.core.HomeCursor
import com.coolapk.kmp.core.HomeRepository
import com.coolapk.kmp.core.createPlatformGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class DigitalLibraryMode { BRAND, CATEGORY }

internal data class DigitalContentState(
    val items: List<DigitalEntity> = emptyList(),
    val page: Int = 0,
    val cursor: HomeCursor? = null,
    val pageContext: String? = null,
    val hasMore: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null,
)

internal data class DigitalUiState(
    val configLoading: Boolean = false,
    val configError: String? = null,
    val tabs: List<DigitalTab> = emptyList(),
    val selectedKey: String? = null,
    val contentByTab: Map<String, DigitalContentState> = emptyMap(),
    val libraryMode: DigitalLibraryMode = DigitalLibraryMode.BRAND,
    val libraryOptions: List<DigitalEntity> = emptyList(),
    val libraryLoading: Boolean = false,
    val libraryError: String? = null,
    val selectedLibraryItem: DigitalEntity? = null,
    val libraryContent: DigitalContentState = DigitalContentState(),
    /** 每个频道当前选中的子栏目（tab.key -> 子栏目 id），未选中时为空。 */
    val subtabByTab: Map<String, String> = emptyMap(),
) {
    val selectedTab: DigitalTab? get() = tabs.firstOrNull { it.key == selectedKey }
    val content: DigitalContentState get() = if (selectedTab?.category == true) libraryContent
        else contentByTab[selectedKey] ?: DigitalContentState()
}

internal class DigitalViewModel(private val repositoryProvider: () -> HomeRepository) : ViewModel() {
    private val _state = MutableStateFlow(DigitalUiState())
    val state: StateFlow<DigitalUiState> = _state.asStateFlow()
    private val repository by lazy { runCatching(repositoryProvider).getOrNull() }
    private var configRequested = false
    private var configRequest = 0
    private var pageRequest = 0
    private var libraryRequest = 0

    fun ensureLoaded() {
        if (!configRequested) loadConfig()
    }

    fun loadConfig() {
        configRequested = true
        val request = ++configRequest
        _state.update { it.copy(configLoading = true, configError = null) }
        viewModelScope.launch {
            runCatching { requireRepository().loadDigitalConfig() }
                .onSuccess { config ->
                    if (request != configRequest) return@onSuccess
                    val selected = config.tabs.firstOrNull { it.key == config.selectedKey }
                        ?: config.tabs.firstOrNull()
                    _state.update {
                        it.copy(configLoading = false, tabs = config.tabs,
                            selectedKey = selected?.key, configError = null)
                    }
                    if (selected != null) selectTab(selected, force = true)
                }
                .onFailure { error ->
                    if (request != configRequest) return@onFailure
                    _state.update {
                        it.copy(configLoading = false,
                            configError = error.message ?: "数码栏目加载失败")
                    }
                }
        }
    }

    fun selectTab(tab: DigitalTab, force: Boolean = false) {
        if (!force && _state.value.selectedKey == tab.key) return
        ++pageRequest
        _state.update { previous ->
            previous.copy(
                selectedKey = tab.key,
                contentByTab = previous.contentByTab.mapValues { (_, value) ->
                    if (value.loading) value.copy(loading = false) else value
                },
                libraryContent = previous.libraryContent.copy(loading = false),
            )
        }
        if (tab.category) {
            if (_state.value.libraryOptions.isEmpty()) loadLibraryOptions()
            else if (_state.value.selectedLibraryItem != null && _state.value.libraryContent.page == 0) {
                loadLibraryProducts()
            }
        } else if (_state.value.contentByTab[tab.key]?.page == null) {
            loadTab(tab)
        }
    }

    fun selectLibraryMode(mode: DigitalLibraryMode) {
        if (_state.value.libraryMode == mode) return
        ++pageRequest
        ++libraryRequest
        _state.update {
            it.copy(libraryMode = mode, libraryOptions = emptyList(), libraryLoading = false,
                libraryError = null, selectedLibraryItem = null,
                libraryContent = DigitalContentState())
        }
        loadLibraryOptions()
    }

    fun selectLibraryItem(item: DigitalEntity) {
        if (_state.value.selectedLibraryItem?.id == item.id) return
        ++pageRequest
        _state.update { it.copy(selectedLibraryItem = item, libraryContent = DigitalContentState()) }
        loadLibraryProducts()
    }

    fun refresh() {
        val snapshot = _state.value
        val tab = snapshot.selectedTab ?: run { loadConfig(); return }
        if (tab.category) {
            if (snapshot.selectedLibraryItem == null) loadLibraryOptions()
            else loadLibraryProducts()
        } else loadTab(tab)
    }

    fun loadMore() {
        val snapshot = _state.value
        val tab = snapshot.selectedTab ?: return
        val content = snapshot.content
        if (content.loading || content.page == 0 || !content.hasMore) return
        if (tab.category) loadLibraryProducts(more = true)
        else loadTab(tab, more = true)
    }

    private fun loadLibraryOptions() {
        val mode = _state.value.libraryMode
        val request = ++libraryRequest
        _state.update { it.copy(libraryLoading = true, libraryError = null) }
        viewModelScope.launch {
            runCatching {
                if (mode == DigitalLibraryMode.BRAND) requireRepository().loadDigitalBrands()
                else requireRepository().loadDigitalCategories()
            }.onSuccess { page ->
                if (request != libraryRequest || mode != _state.value.libraryMode) return@onSuccess
                val options = page.items.distinctBy(DigitalEntity::id)
                val selected = options.firstOrNull { it.id == _state.value.selectedLibraryItem?.id }
                    ?: options.firstOrNull()
                _state.update {
                    it.copy(libraryLoading = false, libraryOptions = options, libraryError = null,
                        selectedLibraryItem = selected,
                        libraryContent = if (selected?.id == it.selectedLibraryItem?.id) it.libraryContent
                            else DigitalContentState())
                }
                if (selected != null && _state.value.libraryContent.page == 0) loadLibraryProducts()
            }.onFailure { error ->
                if (request != libraryRequest) return@onFailure
                _state.update {
                    it.copy(libraryLoading = false,
                        libraryError = error.message ?: "数码品牌和分类加载失败")
                }
            }
        }
    }

    /** 选择频道的子栏目：内容按子栏目地址重新加载，频道本身的选中状态不变。 */
    fun selectSubtab(tab: DigitalTab, subtab: DigitalEntity) {
        val target = subtab.url?.takeIf(String::isNotBlank) ?: return
        if (_state.value.subtabByTab[tab.key] == subtab.id) return
        _state.update {
            it.copy(
                subtabByTab = it.subtabByTab + (tab.key to subtab.id),
                contentByTab = it.contentByTab + (tab.key to DigitalContentState()),
            )
        }
        loadTab(tab)
    }

    private fun loadTab(tab: DigitalTab, more: Boolean = false) {
        val previous = _state.value.contentByTab[tab.key] ?: DigitalContentState()
        val request = ++pageRequest
        _state.update {
            it.copy(contentByTab = it.contentByTab + (tab.key to previous.copy(loading = true, error = null)))
        }
        val requestTab = subtabRequestTab(tab)
        viewModelScope.launch {
            runCatching {
                requireRepository().loadDigitalTab(
                    requestTab, page = if (more) previous.page + 1 else 1,
                    cursor = if (more) previous.cursor else null,
                    pageContext = if (more) previous.pageContext else null,
                )
            }.onSuccess { page ->
                if (request != pageRequest || _state.value.selectedKey != tab.key) return@onSuccess
                val result = previous.merge(page, more)
                _state.update { it.copy(contentByTab = it.contentByTab + (tab.key to result)) }
            }.onFailure { error ->
                if (request != pageRequest || _state.value.selectedKey != tab.key) return@onFailure
                _state.update {
                    it.copy(contentByTab = it.contentByTab +
                        (tab.key to previous.copy(loading = false, error = error.message ?: "栏目加载失败")))
                }
            }
        }
    }

    private fun subtabRequestTab(tab: DigitalTab): DigitalTab {
        val selectedId = _state.value.subtabByTab[tab.key] ?: return tab
        val subtab = tab.entities.firstOrNull { it.id == selectedId } ?: return tab
        return tab.copy(url = subtab.url ?: tab.url)
    }

    private fun loadLibraryProducts(more: Boolean = false) {
        val snapshot = _state.value
        val item = snapshot.selectedLibraryItem ?: return
        val mode = snapshot.libraryMode
        val previous = snapshot.libraryContent
        val request = ++pageRequest
        _state.update { it.copy(libraryContent = previous.copy(loading = true, error = null)) }
        viewModelScope.launch {
            runCatching {
                if (mode == DigitalLibraryMode.BRAND) requireRepository().loadDigitalBrandProducts(
                    item, page = if (more) previous.page + 1 else 1,
                    cursor = if (more) previous.cursor else null,
                ) else requireRepository().loadDigitalCategoryProducts(
                    item, page = if (more) previous.page + 1 else 1,
                    cursor = if (more) previous.cursor else null,
                )
            }.onSuccess { page ->
                if (request != pageRequest || _state.value.selectedLibraryItem?.id != item.id ||
                    _state.value.libraryMode != mode) return@onSuccess
                _state.update { it.copy(libraryContent = previous.merge(page, more)) }
            }.onFailure { error ->
                if (request != pageRequest || _state.value.selectedLibraryItem?.id != item.id) return@onFailure
                _state.update {
                    it.copy(libraryContent = previous.copy(loading = false,
                        error = error.message ?: "产品列表加载失败"))
                }
            }
        }
    }

    private fun DigitalContentState.merge(page: DigitalPage, more: Boolean): DigitalContentState {
        val seen = items.map(DigitalEntity::id).toSet()
        val fresh = if (more) page.items.filterNot { it.id in seen } else page.items
        return copy(
            items = if (more) items + fresh else fresh.distinctBy(DigitalEntity::id),
            page = if (more) this.page + 1 else 1,
            cursor = page.firstItem?.let { first ->
                page.lastItem?.let { last -> HomeCursor(first, last) }
            },
            pageContext = page.pageContext ?: if (more) pageContext else null,
            hasMore = page.hasMore && (!more || fresh.isNotEmpty()),
            loading = false,
            error = null,
        )
    }

    private fun requireRepository(): HomeRepository = repository
        ?: error("Rust 核心尚未就绪，请先构建 Android 原生库")

    companion object {
        val Factory = viewModelFactory {
            initializer { DigitalViewModel { HomeRepository(createPlatformGateway()) } }
        }
    }
}
