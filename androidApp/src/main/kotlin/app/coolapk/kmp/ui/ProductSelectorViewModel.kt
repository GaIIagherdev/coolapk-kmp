package app.coolapk.kmp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.coolapk.kmp.core.DigitalProductDetail
import com.coolapk.kmp.core.firstConfigId
import com.coolapk.kmp.core.HomeRepository
import com.coolapk.kmp.core.SearchCategory
import com.coolapk.kmp.core.SearchResult
import com.coolapk.kmp.core.createPlatformGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val MAX_COMPARE_PRODUCTS = 3

internal data class ProductSelectorUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val page: Int = 0,
    val hasMore: Boolean = true,
    /** 已加入对比的产品（按加入顺序），上限 [MAX_COMPARE_PRODUCTS]。 */
    val selected: List<SearchResult> = emptyList(),
    /** 对比中产品的详情，key 为产品 id；加载失败时值为 null。 */
    val details: Map<String, DigitalProductDetail?> = emptyMap(),
    val compareError: String? = null,
    /** 对比页所需的型号配置 id，解析完成后由界面读取并跳转。 */
    val compareConfigIds: List<String>? = null,
    /** 与 compareConfigIds 对应的产品名，用于对比表头。 */
    val compareTitles: List<String> = emptyList(),
    val comparing: Boolean = false,
)

internal class ProductSelectorViewModel(
    private val repositoryProvider: () -> HomeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ProductSelectorUiState())
    val state: StateFlow<ProductSelectorUiState> = _state.asStateFlow()
    private val repository by lazy { runCatching(repositoryProvider).getOrNull() }
    private var searchRequest = 0

    fun updateQuery(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun search() {
        val query = _state.value.query.trim()
        if (query.isEmpty()) {
            ++searchRequest
            _state.update { it.copy(results = emptyList(), loading = false, error = null, page = 0, hasMore = true) }
            return
        }
        loadResults(query, page = 1)
    }

    fun loadMore() {
        val snapshot = _state.value
        if (snapshot.loading || snapshot.page == 0 || !snapshot.hasMore) return
        loadResults(snapshot.query.trim(), page = snapshot.page + 1)
    }

    private fun loadResults(query: String, page: Int) {
        val source = repository ?: run {
            _state.update { it.copy(error = "数据服务尚未就绪") }
            return
        }
        val request = ++searchRequest
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val result = source.searchByType(query, SearchCategory.PRODUCT, page = page)
                if (request != searchRequest) return@launch
                _state.update { state ->
                    val existing = if (page == 1) emptySet() else state.results.map(SearchResult::id).toSet()
                    val fresh = result.items.filterNot { it.id in existing }.distinctBy(SearchResult::id)
                    state.copy(
                        results = if (page == 1) fresh else state.results + fresh,
                        loading = false,
                        page = page,
                        hasMore = result.hasMore && fresh.isNotEmpty(),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (request == searchRequest) {
                    _state.update { it.copy(loading = false, error = failure.message ?: "搜索失败") }
                }
            }
        }
    }

    /** 加入或移出对比；超过上限时给出提示，不做任何改动。 */
    fun toggle(product: SearchResult) {
        val snapshot = _state.value
        val isSelected = snapshot.selected.any { it.id == product.id }
        if (isSelected) {
            _state.update { it.copy(selected = it.selected.filterNot { p -> p.id == product.id }, compareError = null) }
            return
        }
        if (snapshot.selected.size >= MAX_COMPARE_PRODUCTS) {
            _state.update { it.copy(compareError = "最多同时对比 $MAX_COMPARE_PRODUCTS 款产品") }
            return
        }
        _state.update { it.copy(selected = it.selected + product, compareError = null) }
        loadDetail(product)
    }

    private fun loadDetail(product: SearchResult) {
        val productId = product.targetId ?: product.id
        if (_state.value.details.containsKey(productId)) return
        val source = repository ?: return
        viewModelScope.launch {
            val detail = runCatching { source.loadDigitalProductDetail(productId) }.getOrNull()
            _state.update { it.copy(details = it.details + (productId to detail)) }
        }
    }

    /** 读取已选产品的第一个型号配置，凑齐两个以上后进入对比。 */
    fun startCompare() {
        val snapshot = _state.value
        val source = repository ?: return
        if (snapshot.selected.size < 2 || snapshot.comparing) return
        _state.update { it.copy(comparing = true, compareError = null) }
        viewModelScope.launch {
            val pairs = snapshot.selected.mapNotNull { product ->
                val productId = product.targetId ?: product.id
                val detail = snapshot.details[productId] ?: runCatching { source.loadDigitalProductDetail(productId) }.getOrNull()
                detail?.firstConfigId()?.let { it to product.title }
            }
            if (pairs.size < 2) {
                _state.update { it.copy(comparing = false, compareError = "所选机型缺少可对比配置，请换其他机型") }
            } else {
                _state.update { it.copy(comparing = false, compareConfigIds = pairs.map { p -> p.first }, compareTitles = pairs.map { p -> p.second }) }
            }
        }
    }

    fun consumeCompare() {
        _state.update { it.copy(compareConfigIds = null) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { ProductSelectorViewModel { HomeRepository(createPlatformGateway()) } }
        }
    }
}
