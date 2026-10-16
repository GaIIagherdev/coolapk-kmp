package app.coolapk.kmp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.coolapk.kmp.core.FeedCollection
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

internal enum class FavoritesTab(val label: String) {
    FEEDS("收藏的动态"),
    COLLECTIONS("收藏单"),
}

/** 一个分页列表的状态。cursor 为服务端分页游标，下一页需要原样回传。 */
internal data class PagedListState<T>(
    val items: List<T> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val page: Int = 0,
    val cursor: HomeCursor? = null,
    val hasMore: Boolean = true,
)

internal data class FavoritesUiState(
    val tab: FavoritesTab = FavoritesTab.FEEDS,
    val feeds: PagedListState<FeedItem> = PagedListState(),
    val collections: PagedListState<FeedCollection> = PagedListState(),
    val openedCollection: FeedCollection? = null,
    val collectionItems: PagedListState<FeedItem> = PagedListState(),
)

private data class PageResult<T>(
    val items: List<T>,
    val cursor: HomeCursor?,
    val hasMore: Boolean,
)

internal class FavoritesViewModel(
    private val repositoryProvider: () -> HomeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(FavoritesUiState())
    val state: StateFlow<FavoritesUiState> = _state.asStateFlow()
    private val repository by lazy { runCatching(repositoryProvider).getOrNull() }

    init {
        loadFeeds(page = 1)
    }

    fun select(tab: FavoritesTab) {
        if (_state.value.tab == tab) return
        _state.update { it.copy(tab = tab) }
        when (tab) {
            FavoritesTab.FEEDS -> if (_state.value.feeds.page == 0) loadFeeds(page = 1)
            FavoritesTab.COLLECTIONS -> if (_state.value.collections.page == 0) loadCollections(page = 1)
        }
    }

    fun loadMore() {
        val snapshot = _state.value
        when {
            snapshot.openedCollection != null -> snapshot.collectionItems.nextPage()?.let(::loadCollectionItems)
            snapshot.tab == FavoritesTab.FEEDS -> snapshot.feeds.nextPage()?.let(::loadFeeds)
            else -> snapshot.collections.nextPage()?.let(::loadCollections)
        }
    }

    fun retry() {
        val snapshot = _state.value
        when {
            snapshot.openedCollection != null -> loadCollectionItems(snapshot.collectionItems.nextPage() ?: 1)
            snapshot.tab == FavoritesTab.FEEDS -> loadFeeds(snapshot.feeds.nextPage() ?: 1)
            else -> loadCollections(snapshot.collections.nextPage() ?: 1)
        }
    }

    fun openCollection(collection: FeedCollection) {
        _state.update { it.copy(openedCollection = collection, collectionItems = PagedListState()) }
        loadCollectionItems(page = 1)
    }

    /** 返回 true 表示消费了返回事件（从收藏单内容退回收藏单列表）。 */
    fun closeCollection(): Boolean {
        if (_state.value.openedCollection == null) return false
        _state.update { it.copy(openedCollection = null, collectionItems = PagedListState()) }
        return true
    }

    private fun loadFeeds(page: Int) {
        val source = repository
        fetchPage(
            slot = { it.feeds },
            put = { state, list -> state.copy(feeds = list) },
            key = FeedItem::id,
            page = page,
            isCurrent = { true },
            fetch = { cursor ->
                val result = source?.loadFavoriteFeeds(page, cursor) ?: unavailable()
                PageResult(result.items, result.cursorOrNull(), result.hasMore)
            },
        )
    }

    private fun loadCollections(page: Int) {
        val source = repository
        fetchPage(
            slot = { it.collections },
            put = { state, list -> state.copy(collections = list) },
            key = FeedCollection::id,
            page = page,
            isCurrent = { true },
            fetch = { cursor ->
                val result = source?.loadCollections(page = page, cursor = cursor) ?: unavailable()
                PageResult(
                    items = result.items,
                    cursor = result.firstItem?.let { first -> result.lastItem?.let { last -> HomeCursor(first, last) } },
                    hasMore = result.hasMore,
                )
            },
        )
    }

    private fun loadCollectionItems(page: Int) {
        val collection = _state.value.openedCollection ?: return
        val source = repository
        fetchPage(
            slot = { it.collectionItems },
            put = { state, list -> state.copy(collectionItems = list) },
            key = FeedItem::id,
            page = page,
            isCurrent = { _state.value.openedCollection?.id == collection.id },
            fetch = { cursor ->
                val result = source?.loadCollectionItems(collection.id, page, cursor) ?: unavailable()
                PageResult(result.items, result.cursorOrNull(), result.hasMore)
            },
        )
    }

    private fun <T> fetchPage(
        slot: (FavoritesUiState) -> PagedListState<T>,
        put: (FavoritesUiState, PagedListState<T>) -> FavoritesUiState,
        key: (T) -> String,
        page: Int,
        isCurrent: () -> Boolean,
        fetch: suspend (HomeCursor?) -> PageResult<T>,
    ) {
        val snapshot = slot(_state.value)
        if (snapshot.loading) return
        _state.update { put(it, slot(it).copy(loading = true, error = null)) }
        viewModelScope.launch {
            try {
                val result = fetch(if (page == 1) null else slot(_state.value).cursor)
                if (!isCurrent()) return@launch
                _state.update { put(it, slot(it).merged(page, result, key)) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _state.update { put(it, slot(it).copy(loading = false, error = failure.message ?: "加载失败")) }
            }
        }
    }

    private fun <T> PagedListState<T>.nextPage(): Int? =
        if (loading || page == 0 || !hasMore) null else page + 1

    private fun <T> PagedListState<T>.merged(page: Int, result: PageResult<T>, key: (T) -> String): PagedListState<T> {
        val existing = if (page == 1) emptySet() else items.map(key).toSet()
        val fresh = result.items.filterNot { key(it) in existing }.distinctBy(key)
        return copy(
            items = if (page == 1) fresh else items + fresh,
            loading = false,
            error = null,
            page = page,
            cursor = result.cursor,
            hasMore = result.hasMore && fresh.isNotEmpty(),
        )
    }

    private fun HomePage.cursorOrNull(): HomeCursor? =
        firstItem?.let { first -> lastItem?.let { last -> HomeCursor(first, last) } }

    private fun unavailable(): Nothing = throw IllegalStateException("数据服务尚未就绪")

    companion object {
        fun factory() = viewModelFactory {
            initializer { FavoritesViewModel { HomeRepository(createPlatformGateway()) } }
        }
    }
}
