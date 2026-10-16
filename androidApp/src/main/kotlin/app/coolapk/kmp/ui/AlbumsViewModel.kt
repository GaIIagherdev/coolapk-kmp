package app.coolapk.kmp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.coolapk.kmp.core.HomeRepository
import com.coolapk.kmp.core.ListEntry
import com.coolapk.kmp.core.createPlatformGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 相册分类，listType 与参考项目一致。 */
internal enum class AlbumSort(val listType: String, val label: String) {
    HOT("hot", "热门"),
    NEW("new", "最新"),
}

internal data class AlbumsUiState(
    val sort: AlbumSort = AlbumSort.HOT,
    val list: PagedListState<ListEntry> = PagedListState(),
)

internal class AlbumsViewModel(
    private val repositoryProvider: () -> HomeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AlbumsUiState())
    val state: StateFlow<AlbumsUiState> = _state.asStateFlow()
    private val repository by lazy { runCatching(repositoryProvider).getOrNull() }

    init {
        load(page = 1)
    }

    fun select(sort: AlbumSort) {
        if (_state.value.sort == sort) return
        _state.update { AlbumsUiState(sort = sort) }
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
        val sort = _state.value.sort
        _state.update { it.copy(list = it.list.copy(loading = true, error = null)) }
        viewModelScope.launch {
            try {
                val result = source.loadAlbums(sort.listType, page)
                _state.update { state ->
                    if (state.sort != sort) return@update state
                    val current = state.list
                    val existing = if (page == 1) emptySet() else current.items.map(ListEntry::id).toSet()
                    val fresh = result.items.filterNot { it.id in existing }.distinctBy(ListEntry::id)
                    state.copy(
                        list = current.copy(
                            items = if (page == 1) fresh else current.items + fresh,
                            loading = false,
                            error = null,
                            page = page,
                            hasMore = result.hasMore && fresh.isNotEmpty(),
                        ),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _state.update { it.copy(list = it.list.copy(loading = false, error = failure.message ?: "加载失败")) }
            }
        }
    }

    companion object {
        fun factory() = viewModelFactory {
            initializer { AlbumsViewModel { HomeRepository(createPlatformGateway()) } }
        }
    }
}
