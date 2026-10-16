package app.coolapk.kmp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.coolapk.kmp.core.FeedChannel
import com.coolapk.kmp.core.FeedItem
import com.coolapk.kmp.core.HomeCursor
import com.coolapk.kmp.core.HomePage
import com.coolapk.kmp.core.HomeRepository
import com.coolapk.kmp.core.createPlatformGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 评测区板块，boardTag 与参考项目一致。 */
internal enum class ReviewBoard(val boardTag: String, val label: String) {
    REVIEW("#/board/数码测评", "数码测评"),
    DIGITAL("#/board/数码", "数码"),
    PHONE("#/board/手机", "手机"),
    COMPUTER("#/board/电脑", "电脑"),
    TABLET("#/board/平板", "平板"),
    SYSTEM("#/board/系统", "系统"),
}

internal data class ReviewUiState(
    val board: ReviewBoard = ReviewBoard.REVIEW,
    val list: PagedListState<FeedItem> = PagedListState(),
    /** 板块加载失败、回退到热门动态时的提示。 */
    val notice: String? = null,
)

internal class ReviewViewModel(
    private val repositoryProvider: () -> HomeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ReviewUiState())
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()
    private val repository by lazy { runCatching(repositoryProvider).getOrNull() }

    init {
        load(page = 1)
    }

    fun select(board: ReviewBoard) {
        if (_state.value.board == board) return
        _state.update { ReviewUiState(board = board) }
        load(page = 1)
    }

    fun loadMore() {
        val snapshot = _state.value.list
        if (snapshot.loading || snapshot.page == 0 || !snapshot.hasMore) return
        load(page = snapshot.page + 1)
    }

    fun retry() {
        val snapshot = _state.value.list
        if (snapshot.loading) return
        load(page = if (snapshot.page == 0) 1 else snapshot.page + 1)
    }

    private fun load(page: Int) {
        val source = repository ?: run {
            _state.update { it.copy(list = it.list.copy(error = "数据服务尚未就绪")) }
            return
        }
        val board = _state.value.board
        val cursor = if (page == 1) null else _state.value.list.cursor
        _state.update { it.copy(list = it.list.copy(loading = true, error = null)) }
        viewModelScope.launch {
            try {
                val (result, notice) = try {
                    val boardResult = source.loadBoardFeeds(board.boardTag, page)
                    // 与参考项目一致：板块首页为空时回退到热门动态，并明确提示。
                    if (page == 1 && boardResult.items.isEmpty()) {
                        source.load(FeedChannel.HOT, 1) to "板块暂无内容，已显示热门动态"
                    } else {
                        boardResult to null
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    // 与参考项目一致：板块接口失败时（首页）回退到热门动态，并明确提示。
                    if (page != 1) throw failure
                    source.load(FeedChannel.HOT, 1) to "板块加载失败，已显示热门动态"
                }
                _state.update { state ->
                    if (state.board != board) return@update state
                    state.copy(list = state.list.merged(page, result), notice = notice)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _state.update { it.copy(list = it.list.copy(loading = false, error = failure.message ?: "加载失败")) }
            }
        }
    }

    private fun PagedListState<FeedItem>.merged(page: Int, result: HomePage): PagedListState<FeedItem> {
        val existing = if (page == 1) emptySet() else items.map(FeedItem::id).toSet()
        val fresh = result.items.filterNot { it.id in existing }.distinctBy(FeedItem::id)
        val cursor = result.firstItem?.let { first -> result.lastItem?.let { last -> HomeCursor(first, last) } }
        return copy(
            items = if (page == 1) fresh else items + fresh,
            loading = false,
            error = null,
            page = page,
            cursor = cursor,
            hasMore = result.hasMore && fresh.isNotEmpty(),
        )
    }

    companion object {
        fun factory() = viewModelFactory {
            initializer { ReviewViewModel { HomeRepository(createPlatformGateway()) } }
        }
    }
}
