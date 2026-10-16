package app.coolapk.kmp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.coolapk.kmp.core.AppItem
import com.coolapk.kmp.core.HomeRepository
import com.coolapk.kmp.core.createPlatformGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 游戏中心分类，对应游戏接口的 type 参数。 */
internal enum class GameCategory(val key: String, val label: String) {
    HOT("hot", "热门"),
    NEW("new", "新游"),
    SINGLE("single", "单机"),
    ONLINE("online", "网游"),
    CASUAL("casual", "休闲"),
    INDIE("indie", "独立"),
}

internal data class GameCenterUiState(
    val category: GameCategory = GameCategory.HOT,
    val list: PagedListState<AppItem> = PagedListState(),
)

internal class GameCenterViewModel(
    private val repositoryProvider: () -> HomeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(GameCenterUiState())
    val state: StateFlow<GameCenterUiState> = _state.asStateFlow()
    private val repository by lazy { runCatching(repositoryProvider).getOrNull() }

    init {
        load(page = 1)
    }

    fun select(category: GameCategory) {
        if (_state.value.category == category) return
        _state.update { GameCenterUiState(category = category) }
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
        val category = _state.value.category
        _state.update { it.copy(list = it.list.copy(loading = true, error = null)) }
        viewModelScope.launch {
            try {
                val result = source.loadGames(page, category.key)
                _state.update { state ->
                    if (state.category != category) return@update state
                    val current = state.list
                    val existing = if (page == 1) emptySet() else current.items.map(AppItem::id).toSet()
                    val fresh = result.items.filterNot { it.id in existing }.distinctBy(AppItem::id)
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
            initializer { GameCenterViewModel { HomeRepository(createPlatformGateway()) } }
        }
    }
}
