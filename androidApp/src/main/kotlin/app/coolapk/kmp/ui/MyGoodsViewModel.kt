package app.coolapk.kmp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.coolapk.kmp.core.DigitalEntity
import com.coolapk.kmp.core.HomeRepository
import com.coolapk.kmp.core.createPlatformGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 我的装备的五个分类，key 与服务端 / 数据接口的类型参数一致。 */
internal enum class MyGoodsKind(val label: String, val key: String) {
    ALL("全部", "all"),
    WISH("想买", "wish"),
    BUY("买过", "buy"),
    STORE("商品店铺", "store"),
    ALBUM("产品专辑", "album"),
}

internal data class MyGoodsListState(
    val items: List<DigitalEntity> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val page: Int = 0,
    val hasMore: Boolean = true,
)

internal data class MyGoodsUiState(
    val kind: MyGoodsKind = MyGoodsKind.ALL,
    val lists: Map<MyGoodsKind, MyGoodsListState> = emptyMap(),
) {
    fun current(): MyGoodsListState = lists[kind] ?: MyGoodsListState()
}

internal class MyGoodsViewModel(
    private val uid: String,
    private val repositoryProvider: () -> HomeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(MyGoodsUiState())
    val state: StateFlow<MyGoodsUiState> = _state.asStateFlow()
    private val repository by lazy { runCatching(repositoryProvider).getOrNull() }

    init {
        load(MyGoodsKind.ALL, page = 1)
    }

    fun select(kind: MyGoodsKind) {
        _state.update { it.copy(kind = kind) }
        val list = _state.value.lists[kind]
        if (list == null || list.page == 0) load(kind, page = 1)
    }

    fun loadMore() {
        val kind = _state.value.kind
        val list = _state.value.current()
        if (list.loading || list.page == 0 || !list.hasMore) return
        load(kind, page = list.page + 1)
    }

    fun retry() {
        val kind = _state.value.kind
        val list = _state.value.current()
        if (list.loading) return
        load(kind, page = if (list.page == 0) 1 else list.page + 1)
    }

    private fun load(kind: MyGoodsKind, page: Int) {
        val source = repository ?: run {
            _state.update { state ->
                state.copy(lists = state.lists + (kind to MyGoodsListState(error = "数据服务尚未就绪")))
            }
            return
        }
        _state.update { state ->
            val previous = state.lists[kind] ?: MyGoodsListState()
            state.copy(lists = state.lists + (kind to previous.copy(loading = true, error = null)))
        }
        viewModelScope.launch {
            try {
                val result = source.loadMyGoods(uid, kind.key, page)
                _state.update { state ->
                    val previous = state.lists[kind] ?: MyGoodsListState()
                    val existing = if (page == 1) emptySet() else previous.items.map(DigitalEntity::id).toSet()
                    val fresh = result.items.filterNot { it.id in existing }.distinctBy(DigitalEntity::id)
                    val next = previous.copy(
                        items = if (page == 1) fresh else previous.items + fresh,
                        loading = false,
                        error = null,
                        page = page,
                        hasMore = result.hasMore && fresh.isNotEmpty(),
                    )
                    state.copy(lists = state.lists + (kind to next))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _state.update { state ->
                    val previous = state.lists[kind] ?: MyGoodsListState()
                    state.copy(lists = state.lists + (kind to previous.copy(
                        loading = false,
                        error = failure.message ?: "加载失败",
                    )))
                }
            }
        }
    }

    companion object {
        fun factory(uid: String) = viewModelFactory {
            initializer { MyGoodsViewModel(uid) { HomeRepository(createPlatformGateway()) } }
        }
    }
}
