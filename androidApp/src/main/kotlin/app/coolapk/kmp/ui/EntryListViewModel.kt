package app.coolapk.kmp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.coolapk.kmp.core.HomeRepository
import com.coolapk.kmp.core.createPlatformGateway
import androidx.lifecycle.viewModelScope
import com.coolapk.kmp.core.ListEntry
import com.coolapk.kmp.core.ListEntryPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 通用的条目分页列表，供好物清单等只需要“标题、简介、图片”的列表复用。 */
internal class EntryListViewModel(
    private val load: suspend (page: Int) -> ListEntryPage,
) : ViewModel() {
    private val _state = MutableStateFlow(PagedListState<ListEntry>())
    val state: StateFlow<PagedListState<ListEntry>> = _state.asStateFlow()

    init {
        fetch(page = 1)
    }

    fun loadMore() {
        val snapshot = _state.value
        if (snapshot.loading || snapshot.page == 0 || !snapshot.hasMore) return
        fetch(page = snapshot.page + 1)
    }

    fun retry() {
        val snapshot = _state.value
        if (snapshot.loading) return
        fetch(page = if (snapshot.page == 0) 1 else snapshot.page + 1)
    }

    private fun fetch(page: Int) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val result = load(page)
                _state.update { current ->
                    val existing = if (page == 1) emptySet() else current.items.map(ListEntry::id).toSet()
                    val fresh = result.items.filterNot { it.id in existing }.distinctBy(ListEntry::id)
                    current.copy(
                        items = if (page == 1) fresh else current.items + fresh,
                        loading = false,
                        error = null,
                        page = page,
                        hasMore = result.hasMore && fresh.isNotEmpty(),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _state.update { it.copy(loading = false, error = failure.message ?: "加载失败") }
            }
        }
    }

    companion object {
        /** 关注的话题，供「我的」页的「我的卡片」复用。 */
        /** 浏览历史：参考项目的 /member/hitHistoryList。 */
        fun feedHistoryFactory() = viewModelFactory {
            initializer {
                EntryListViewModel { page ->
                    val result = HomeRepository(createPlatformGateway()).loadFeedHistory(page)
                    ListEntryPage(
                        items = result.items.map { ListEntry(id = it.id, title = it.title, subtitle = it.description, imageUrl = it.imageUrl) },
                        hasMore = result.hasMore,
                    )
                }
            }
        }

        fun followedTopicsFactory() = viewModelFactory {
            initializer { EntryListViewModel { page -> HomeRepository(createPlatformGateway()).loadFollowedTopics(page) } }
        }
    }
}
