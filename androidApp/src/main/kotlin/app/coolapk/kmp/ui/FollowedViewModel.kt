package app.coolapk.kmp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.coolapk.kmp.core.ListEntry
import com.coolapk.kmp.core.ListEntryPage
import com.coolapk.kmp.core.HomeRepository
import com.coolapk.kmp.core.createPlatformGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class FollowedTab(val label: String) {
    FORUMS("关注的论坛"),
    TOPICS("关注的话题"),
}

internal data class FollowedUiState(
    val tab: FollowedTab = FollowedTab.FORUMS,
    val forums: PagedListState<ListEntry> = PagedListState(),
    val topics: PagedListState<ListEntry> = PagedListState(),
)

internal class FollowedViewModel(
    private val uid: String,
    private val repositoryProvider: () -> HomeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(FollowedUiState())
    val state: StateFlow<FollowedUiState> = _state.asStateFlow()
    private val repository by lazy { runCatching(repositoryProvider).getOrNull() }

    init {
        load(FollowedTab.FORUMS, page = 1)
    }

    fun select(tab: FollowedTab) {
        if (_state.value.tab == tab) return
        _state.update { it.copy(tab = tab) }
        if (slot(_state.value, tab).page == 0) load(tab, page = 1)
    }

    fun loadMore() {
        val tab = _state.value.tab
        val snapshot = slot(_state.value, tab)
        if (snapshot.loading || snapshot.page == 0 || !snapshot.hasMore) return
        load(tab, page = snapshot.page + 1)
    }

    fun retry() {
        val tab = _state.value.tab
        val snapshot = slot(_state.value, tab)
        if (snapshot.loading) return
        load(tab, page = if (snapshot.page == 0) 1 else snapshot.page + 1)
    }

    private fun slot(state: FollowedUiState, tab: FollowedTab): PagedListState<ListEntry> =
        if (tab == FollowedTab.FORUMS) state.forums else state.topics

    private fun update(tab: FollowedTab, transform: (PagedListState<ListEntry>) -> PagedListState<ListEntry>) {
        _state.update { state ->
            if (tab == FollowedTab.FORUMS) state.copy(forums = transform(state.forums))
            else state.copy(topics = transform(state.topics))
        }
    }

    private fun load(tab: FollowedTab, page: Int) {
        val source = repository ?: run {
            update(tab) { it.copy(error = "数据服务尚未就绪") }
            return
        }
        update(tab) { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val result: ListEntryPage = if (tab == FollowedTab.FORUMS) {
                    source.loadFollowedForums(uid, page)
                } else {
                    source.loadFollowedTopics(page)
                }
                update(tab) { current ->
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
                update(tab) { it.copy(loading = false, error = failure.message ?: "加载失败") }
            }
        }
    }

    companion object {
        fun factory(uid: String) = viewModelFactory {
            initializer { FollowedViewModel(uid) { HomeRepository(createPlatformGateway()) } }
        }
    }
}
