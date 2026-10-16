package app.coolapk.kmp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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

internal class HistoryViewModel(
    private val repositoryProvider: () -> HomeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PagedListState<FeedItem>())
    val state: StateFlow<PagedListState<FeedItem>> = _state.asStateFlow()
    private val repository by lazy { runCatching(repositoryProvider).getOrNull() }

    init {
        load(page = 1)
    }

    fun loadMore() {
        val snapshot = _state.value
        if (snapshot.loading || snapshot.page == 0 || !snapshot.hasMore) return
        load(page = snapshot.page + 1)
    }

    fun retry() {
        val snapshot = _state.value
        if (snapshot.loading) return
        load(page = if (snapshot.page == 0) 1 else snapshot.page + 1)
    }

    private fun load(page: Int) {
        val source = repository ?: run {
            _state.update { it.copy(error = "数据服务尚未就绪") }
            return
        }
        val cursor = if (page == 1) null else _state.value.cursor
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val result = source.loadRecentHistory(page, cursor)
                _state.update { state -> state.merged(page, result) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _state.update { it.copy(loading = false, error = failure.message ?: "加载失败") }
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
            initializer { HistoryViewModel { HomeRepository(createPlatformGateway()) } }
        }
    }
}
