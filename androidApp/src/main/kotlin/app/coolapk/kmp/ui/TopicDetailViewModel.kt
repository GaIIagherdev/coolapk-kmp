package app.coolapk.kmp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.coolapk.kmp.core.FeedItem
import com.coolapk.kmp.core.HomeCursor
import com.coolapk.kmp.core.HomeRepository
import com.coolapk.kmp.core.TopicDetail
import com.coolapk.kmp.core.TopicFeedSort
import com.coolapk.kmp.core.createPlatformGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class TopicDetailUiState(
    val tag: String = "",
    val detail: TopicDetail? = null,
    val detailLoading: Boolean = false,
    val detailError: String? = null,
    val feeds: List<FeedItem> = emptyList(),
    val feedsLoading: Boolean = false,
    val feedsError: String? = null,
    val page: Int = 0,
    val hasMore: Boolean = true,
    val cursor: HomeCursor? = null,
    val sort: TopicFeedSort = TopicFeedSort.DEFAULT,
)

internal class TopicDetailViewModel(
    private val repositoryProvider: () -> HomeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TopicDetailUiState())
    val state: StateFlow<TopicDetailUiState> = _state.asStateFlow()

    private val repository by lazy { runCatching(repositoryProvider).getOrNull() }
    private var generation = 0
    private var detailRequest = 0
    private var feedRequest = 0

    fun open(tag: String) {
        val cleanTag = tag.trim().trim('#').trim()
        if (cleanTag == _state.value.tag && _state.value.page > 0) return
        ++generation
        ++detailRequest
        ++feedRequest
        _state.value = TopicDetailUiState(tag = cleanTag)
        if (cleanTag.isBlank()) {
            _state.update { it.copy(detailError = "话题名称无效", hasMore = false) }
            return
        }
        loadDetail()
        loadFeeds(reset = true)
    }

    fun refresh() {
        loadDetail()
        loadFeeds(reset = true)
    }

    fun selectSort(sort: TopicFeedSort) {
        if (_state.value.sort == sort) return
        _state.update { it.copy(sort = sort) }
        loadFeeds(reset = true)
    }

    fun loadMore() = loadFeeds(reset = false)

    private fun loadDetail() {
        val tag = _state.value.tag.takeIf(String::isNotBlank) ?: return
        val requestGeneration = generation
        val request = ++detailRequest
        _state.update { it.copy(detailLoading = true, detailError = null) }
        viewModelScope.launch {
            try {
                val detail = repository?.loadTopicDetail(tag) ?: error("数据服务尚未就绪")
                if (requestGeneration != generation || request != detailRequest) return@launch
                _state.update { it.copy(detail = detail, detailLoading = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (requestGeneration != generation || request != detailRequest) return@launch
                _state.update {
                    it.copy(detailLoading = false,
                        detailError = failure.message?.takeIf(String::isNotBlank) ?: "话题概况加载失败")
                }
            }
        }
    }

    private fun loadFeeds(reset: Boolean) {
        val snapshot = _state.value
        val tag = snapshot.tag.takeIf(String::isNotBlank) ?: return
        if (!reset && (snapshot.feedsLoading || !snapshot.hasMore || snapshot.page == 0)) return
        val requestGeneration = generation
        val request = ++feedRequest
        val nextPage = if (reset) 1 else snapshot.page + 1
        _state.update {
            it.copy(
                feedsLoading = true,
                feedsError = null,
                feeds = if (reset) emptyList() else it.feeds,
                page = if (reset) 0 else it.page,
                hasMore = if (reset) true else it.hasMore,
                cursor = if (reset) null else it.cursor,
            )
        }
        viewModelScope.launch {
            try {
                val result = repository?.loadTopicFeeds(
                    tag, nextPage, snapshot.sort, if (reset) null else snapshot.cursor,
                ) ?: error("数据服务尚未就绪")
                if (requestGeneration != generation || request != feedRequest) return@launch
                _state.update { state ->
                    val previousIds = state.feeds.map(FeedItem::id).toSet()
                    val newItems = result.items.filter { it.id !in previousIds }
                    state.copy(
                        feeds = state.feeds + newItems,
                        feedsLoading = false,
                        page = nextPage,
                        hasMore = result.hasMore && newItems.isNotEmpty(),
                        cursor = result.firstItem?.let { first ->
                            result.lastItem?.let { last -> HomeCursor(first, last) }
                        },
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (requestGeneration != generation || request != feedRequest) return@launch
                _state.update {
                    it.copy(feedsLoading = false,
                        feedsError = failure.message?.takeIf(String::isNotBlank) ?: "话题动态加载失败")
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { TopicDetailViewModel { HomeRepository(createPlatformGateway()) } }
        }
    }
}
