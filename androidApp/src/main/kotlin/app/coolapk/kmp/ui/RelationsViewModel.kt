package app.coolapk.kmp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.coolapk.kmp.core.HomeRepository
import com.coolapk.kmp.core.UserSummary
import com.coolapk.kmp.core.createPlatformGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class RelationsUiState(
    val kind: RelationKind,
    val items: List<UserSummary> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val page: Int = 0,
    val hasMore: Boolean = true,
)

internal class RelationsViewModel(
    private val uid: String,
    initialKind: RelationKind,
    private val repositoryProvider: () -> HomeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(RelationsUiState(kind = initialKind))
    val state: StateFlow<RelationsUiState> = _state.asStateFlow()
    private val repository by lazy { runCatching(repositoryProvider).getOrNull() }

    init {
        load(page = 1)
    }

    fun select(kind: RelationKind) {
        if (_state.value.kind == kind) return
        _state.update { RelationsUiState(kind = kind) }
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
        val kind = _state.value.kind
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val result = if (kind == RelationKind.FANS) {
                    source.loadFansUsers(uid, page)
                } else {
                    source.loadFollowUsers(uid, page, kind.apiCategory ?: "follow")
                }
                if (_state.value.kind != kind) return@launch
                _state.update { state ->
                    val existing = if (page == 1) emptySet() else state.items.map(UserSummary::uid).toSet()
                    val fresh = result.items.filterNot { it.uid in existing }.distinctBy(UserSummary::uid)
                    state.copy(
                        items = if (page == 1) fresh else state.items + fresh,
                        loading = false,
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
        fun factory(uid: String, kind: RelationKind) = viewModelFactory {
            initializer { RelationsViewModel(uid, kind) { HomeRepository(createPlatformGateway()) } }
        }
    }
}
