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

internal data class BlacklistUiState(
    val items: List<UserSummary> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val page: Int = 0,
    val hasMore: Boolean = true,
    /** 移出黑名单失败时的提示，界面显示后调用 [BlacklistViewModel.consumeMessage] 清除。 */
    val message: String? = null,
)

internal class BlacklistViewModel(
    private val repositoryProvider: () -> HomeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BlacklistUiState())
    val state: StateFlow<BlacklistUiState> = _state.asStateFlow()
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

    /** 乐观移除：先从列表隐藏，接口失败时恢复原位置并提示。 */
    fun unblock(user: UserSummary) {
        val source = repository ?: return
        val before = _state.value.items
        val index = before.indexOfFirst { it.uid == user.uid }
        if (index < 0) return
        _state.update { it.copy(items = it.items.filterNot { item -> item.uid == user.uid }) }
        viewModelScope.launch {
            val result = source.removeFromBlackList(user.uid)
            if (result.isFailure) {
                _state.update { state ->
                    val restored = state.items.toMutableList().apply { add(index.coerceAtMost(size), user) }
                    state.copy(items = restored, message = "移出黑名单失败，请稍后重试")
                }
            }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun load(page: Int) {
        val source = repository ?: run {
            _state.update { it.copy(error = "数据服务尚未就绪") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val result = source.loadBlackList(page)
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
        fun factory() = viewModelFactory {
            initializer { BlacklistViewModel { HomeRepository(createPlatformGateway()) } }
        }
    }
}
