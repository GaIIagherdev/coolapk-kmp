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

internal class EventsViewModel(
    private val repositoryProvider: () -> HomeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PagedListState<ListEntry>())
    val state: StateFlow<PagedListState<ListEntry>> = _state.asStateFlow()
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
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val result = source.loadEvents(page)
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
        fun factory() = viewModelFactory {
            initializer { EventsViewModel { HomeRepository(createPlatformGateway()) } }
        }
    }
}
