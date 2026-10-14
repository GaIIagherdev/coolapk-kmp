package app.coolapk.kmp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.coolapk.kmp.core.DigitalProductDetail
import com.coolapk.kmp.core.FeedItem
import com.coolapk.kmp.core.HomeRepository
import com.coolapk.kmp.core.ProductRating
import com.coolapk.kmp.core.ProductRatingChart
import com.coolapk.kmp.core.createPlatformGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 产品详情的分页列表（点评、讨论共用结构）。 */
internal data class ProductPagedState<T>(
    val items: List<T> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val page: Int = 0,
    val hasMore: Boolean = true,
)

internal enum class ProductDetailTab(val label: String) {
    OVERVIEW("概览"),
    RATINGS("点评"),
    DISCUSS("讨论"),
}

internal data class DigitalProductDetailUiState(
    val productId: String? = null,
    val detail: DigitalProductDetail? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val chart: ProductRatingChart? = null,
    val chartPeriod: String = "week",
    val ratings: ProductPagedState<ProductRating> = ProductPagedState(),
    val ratingStar: Int = 0,
    val ratingSubmitting: Boolean = false,
    val discussions: ProductPagedState<FeedItem> = ProductPagedState(),
    val wish: Boolean = false,
    val follow: Boolean = false,
    val actionError: String? = null,
)

internal class DigitalProductDetailViewModel(
    private val repositoryProvider: () -> HomeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DigitalProductDetailUiState())
    val state: StateFlow<DigitalProductDetailUiState> = _state.asStateFlow()

    private val repository by lazy { runCatching(repositoryProvider).getOrNull() }
    private var requestId = 0

    fun load(productId: String, force: Boolean = false) {
        val id = productId.trim()
        val previous = _state.value
        if (id.isEmpty()) {
            ++requestId
            _state.value = DigitalProductDetailUiState(error = "产品 ID 无效")
            return
        }
        if (!force && previous.productId == id && (previous.loading || previous.detail != null)) return

        val request = ++requestId
        val sameProduct = previous.productId == id
        _state.value = DigitalProductDetailUiState(
            productId = id,
            detail = previous.detail?.takeIf { sameProduct },
            loading = true,
            chart = previous.chart?.takeIf { sameProduct },
            chartPeriod = previous.chartPeriod,
            ratings = if (sameProduct) previous.ratings else ProductPagedState(),
            discussions = if (sameProduct) previous.discussions else ProductPagedState(),
            wish = previous.wish.takeIf { sameProduct } ?: false,
            follow = previous.follow.takeIf { sameProduct } ?: false,
        )
        viewModelScope.launch {
            try {
                val detail = repository?.loadDigitalProductDetail(id)
                    ?: error("Rust 核心尚未就绪，请先构建 Android 原生库")
                if (request != requestId) return@launch
                _state.update { it.copy(detail = detail, loading = false, error = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (request != requestId) return@launch
                _state.update {
                    it.copy(loading = false, error = failure.message?.takeIf(String::isNotBlank)
                        ?: "产品详情加载失败")
                }
            }
        }
        loadChart(id, request)
    }

    private fun loadChart(productId: String, request: Int) {
        val source = repository ?: return
        viewModelScope.launch {
            try {
                val chart = source.loadProductRatingChart(productId)
                if (request == requestId) _state.update { it.copy(chart = chart) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // 趋势图是辅助信息，失败时静默隐藏，不影响概览
            }
        }
    }

    fun selectChartPeriod(period: String) {
        _state.update { it.copy(chartPeriod = period) }
    }

    fun openRatings() {
        val snapshot = _state.value
        if (snapshot.ratings.loading || snapshot.ratings.page > 0) return
        loadRatings(page = 1)
    }

    fun loadMoreRatings() {
        val snapshot = _state.value
        if (snapshot.ratings.loading || snapshot.ratings.page == 0 || !snapshot.ratings.hasMore) return
        loadRatings(page = snapshot.ratings.page + 1)
    }

    fun setRatingStar(star: Int) {
        if (_state.value.ratingStar == star) return
        _state.update { it.copy(ratingStar = star, ratings = ProductPagedState()) }
        loadRatings(page = 1)
    }

    private fun loadRatings(page: Int) {
        val snapshot = _state.value
        val id = snapshot.productId ?: return
        val source = repository ?: return
        val star = snapshot.ratingStar
        val request = requestId
        _state.update { it.copy(ratings = it.ratings.copy(loading = true, error = null)) }
        viewModelScope.launch {
            try {
                val result = source.loadProductRatings(id, star = star, page = page)
                if (request != requestId) return@launch
                _state.update { state ->
                    val existing = if (page == 1) emptySet() else state.ratings.items.map { it.id }.toSet()
                    val fresh = result.items.filterNot { it.id in existing }
                    state.copy(ratings = state.ratings.copy(
                        items = if (page == 1) fresh else state.ratings.items + fresh,
                        loading = false,
                        page = page,
                        hasMore = result.hasMore && fresh.isNotEmpty(),
                    ))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (request == requestId) {
                    _state.update {
                        it.copy(ratings = it.ratings.copy(loading = false,
                            error = failure.message ?: "点评加载失败"))
                    }
                }
            }
        }
    }

    fun openDiscussions() {
        val snapshot = _state.value
        if (snapshot.discussions.loading || snapshot.discussions.page > 0) return
        loadDiscussions(page = 1)
    }

    fun loadMoreDiscussions() {
        val snapshot = _state.value
        if (snapshot.discussions.loading || snapshot.discussions.page == 0 || !snapshot.discussions.hasMore) return
        loadDiscussions(page = snapshot.discussions.page + 1)
    }

    private fun loadDiscussions(page: Int) {
        val snapshot = _state.value
        val id = snapshot.productId ?: return
        val source = repository ?: return
        val request = requestId
        _state.update { it.copy(discussions = it.discussions.copy(loading = true, error = null)) }
        viewModelScope.launch {
            try {
                val result = source.loadProductFeeds(id, page)
                if (request != requestId) return@launch
                _state.update { state ->
                    val existing = if (page == 1) emptySet() else state.discussions.items.map(FeedItem::id).toSet()
                    val fresh = result.items.filterNot { it.id in existing }.distinctBy(FeedItem::id)
                    state.copy(discussions = state.discussions.copy(
                        items = if (page == 1) fresh else state.discussions.items + fresh,
                        loading = false,
                        page = page,
                        hasMore = result.hasMore && fresh.isNotEmpty(),
                    ))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (request == requestId) {
                    _state.update {
                        it.copy(discussions = it.discussions.copy(loading = false,
                            error = failure.message ?: "讨论加载失败"))
                    }
                }
            }
        }
    }

    /** 想要/取消想要：先乐观更新，失败时回滚并提示。 */
    fun toggleWish() {
        val snapshot = _state.value
        val id = snapshot.productId ?: return
        val source = repository ?: return
        val target = !snapshot.wish
        _state.update { it.copy(wish = target, actionError = null) }
        viewModelScope.launch {
            source.setProductWish(id, target).onFailure { failure ->
                _state.update { it.copy(wish = !target, actionError = failure.message ?: "操作失败") }
            }
        }
    }

    /** 关注/取消关注产品：先乐观更新，失败时回滚并提示。 */
    fun toggleFollow() {
        val snapshot = _state.value
        val id = snapshot.productId ?: return
        val source = repository ?: return
        val target = !snapshot.follow
        _state.update { it.copy(follow = target, actionError = null) }
        viewModelScope.launch {
            source.setProductFollow(id, target).onFailure { failure ->
                _state.update { it.copy(follow = !target, actionError = failure.message ?: "操作失败") }
            }
        }
    }

    fun submitRating(score: Int, message: String, buyStatus: Boolean, onDone: () -> Unit) {
        val snapshot = _state.value
        val id = snapshot.productId ?: return
        val source = repository ?: return
        if (snapshot.ratingSubmitting) return
        _state.update { it.copy(ratingSubmitting = true, actionError = null) }
        viewModelScope.launch {
            source.submitProductRating(id, score, message, buyStatus)
                .onSuccess {
                    _state.update { it.copy(ratingSubmitting = false) }
                    // 发布后从第一页刷新点评列表
                    _state.update { it.copy(ratings = ProductPagedState(), ratingStar = 0) }
                    loadRatings(page = 1)
                    onDone()
                }
                .onFailure { failure ->
                    _state.update { it.copy(ratingSubmitting = false, actionError = failure.message ?: "点评发布失败") }
                }
        }
    }

    fun retry() {
        _state.value.productId?.let { load(it, force = true) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { DigitalProductDetailViewModel { HomeRepository(createPlatformGateway()) } }
        }
    }
}
