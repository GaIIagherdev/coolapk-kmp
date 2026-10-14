package app.coolapk.kmp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.coolapk.kmp.core.DigitalProductDetail
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

    companion object {
        val Factory = viewModelFactory {
            initializer { ProductSelectorViewModel { HomeRepository(createPlatformGateway()) } }
        }
    }
}
