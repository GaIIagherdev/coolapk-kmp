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

/** 新建或编辑收藏单的表单。id 为空表示新建。 */
internal data class CollectionDraft(
    val id: String? = null,
    val title: String = "",
    val description: String = "",
    val isOpen: Boolean = true,
) {
    val isNew: Boolean get() = id == null
    val canSubmit: Boolean get() = title.isNotBlank()
}

internal const val CollectionTitleMaxLength = 30
internal const val CollectionDescriptionMaxLength = 100

internal data class FavoritesUiState(
    val tab: FavoritesTab = FavoritesTab.FEEDS,
    val feeds: PagedListState<FeedItem> = PagedListState(),
    val collections: PagedListState<FeedCollection> = PagedListState(),
    val openedCollection: FeedCollection? = null,
    val collectionItems: PagedListState<FeedItem> = PagedListState(),
    /** 非空时显示新建/编辑收藏单弹层。 */
    val editor: CollectionDraft? = null,
    val editorError: String? = null,
    val submitting: Boolean = false,
    /** 非空时显示删除确认弹层。 */
    val pendingDelete: FeedCollection? = null,
    /** 一次性提示，界面显示后调用 [FavoritesViewModel.consumeMessage] 清除。 */
    val message: String? = null,
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

    fun openCreateEditor() {
        _state.update { it.copy(editor = CollectionDraft(), editorError = null) }
    }

    fun openEditEditor(collection: FeedCollection) {
        _state.update {
            it.copy(
                editor = CollectionDraft(
                    id = collection.id,
                    title = collection.title,
                    description = collection.description,
                    isOpen = collection.isOpen,
                ),
                editorError = null,
            )
        }
    }

    fun updateDraftTitle(value: String) {
        _state.update { state ->
            state.copy(editor = state.editor?.copy(title = value.take(CollectionTitleMaxLength)), editorError = null)
        }
    }

    fun updateDraftDescription(value: String) {
        _state.update { state ->
            state.copy(
                editor = state.editor?.copy(description = value.take(CollectionDescriptionMaxLength)),
                editorError = null,
            )
        }
    }

    fun setDraftOpen(open: Boolean) {
        _state.update { state -> state.copy(editor = state.editor?.copy(isOpen = open)) }
    }

    fun dismissEditor() {
        if (_state.value.submitting) return
        _state.update { it.copy(editor = null, editorError = null) }
    }

    /**
     * 提交新建或编辑。失败时保留弹层并把原因显示在表单里，避免用户重填；
     * 新建成功后重新拉取第一页，服务端才是收藏单 ID 的唯一来源。
     */
    fun submitEditor() {
        val snapshot = _state.value
        val draft = snapshot.editor ?: return
        if (snapshot.submitting) return
        val title = draft.title.trim()
        val description = draft.description.trim()
        if (title.isEmpty()) {
            _state.update { it.copy(editorError = "收藏单标题不能为空") }
            return
        }
        val source = repository
        if (source == null) {
            _state.update { it.copy(editorError = "数据服务尚未就绪") }
            return
        }
        val collectionId = draft.id
        _state.update { it.copy(submitting = true, editorError = null) }
        viewModelScope.launch {
            val result = if (collectionId == null) {
                source.createCollection(title, description, isOpen = draft.isOpen)
            } else {
                source.updateCollection(collectionId, title, description, isOpen = draft.isOpen)
            }
            if (result.isFailure) {
                _state.update {
                    it.copy(
                        submitting = false,
                        editorError = result.exceptionOrNull()?.message ?: "保存失败，请稍后重试",
                    )
                }
                return@launch
            }
            _state.update { state ->
                state.copy(
                    tab = if (collectionId == null) FavoritesTab.COLLECTIONS else state.tab,
                    collections = if (collectionId == null) {
                        state.collections
                    } else {
                        state.collections.replacing(collectionId, title, description, draft.isOpen)
                    },
                    openedCollection = collectionId?.let { id ->
                        state.openedCollection?.updated(id, title, description, draft.isOpen)
                    },
                    editor = null,
                    editorError = null,
                    submitting = false,
                    message = if (collectionId == null) "已创建收藏单" else "已保存修改",
                )
            }
            if (collectionId == null) loadCollections(page = 1)
        }
    }

    fun askDelete(collection: FeedCollection) {
        _state.update { it.copy(pendingDelete = collection) }
    }

    fun dismissDelete() {
        _state.update { it.copy(pendingDelete = null) }
    }

    /** 乐观删除：先从列表移除，接口失败时回到原位置并提示。 */
    fun confirmDelete() {
        val snapshot = _state.value
        val target = snapshot.pendingDelete ?: return
        val source = repository ?: run {
            _state.update { it.copy(pendingDelete = null, message = "数据服务尚未就绪") }
            return
        }
        val before = snapshot.collections.items
        val index = before.indexOfFirst { it.id == target.id }
        _state.update {
            it.copy(
                pendingDelete = null,
                collections = it.collections.copy(items = it.collections.items.filterNot { item -> item.id == target.id }),
            )
        }
        viewModelScope.launch {
            val result = source.deleteCollection(target.id)
            if (result.isFailure) {
                _state.update { state ->
                    val restored = state.collections.items.toMutableList().apply {
                        add(index.coerceIn(0, size), target)
                    }
                    state.copy(
                        collections = state.collections.copy(items = restored),
                        message = result.exceptionOrNull()?.message ?: "删除失败，请稍后重试",
                    )
                }
            } else {
                _state.update { it.copy(message = "已删除收藏单") }
            }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    /**
     * 从当前收藏单移出内容。乐观移除，失败时放回原位置并提示；
     * 条目 ID 与动态 ID 不同，缺失时只提示，不发请求。
     */
    fun removeFromCollection(feed: FeedItem) {
        val snapshot = _state.value
        val collection = snapshot.openedCollection ?: return
        val itemId = feed.collectionItemId
        if (itemId.isNullOrBlank()) {
            _state.update { it.copy(message = "这条内容缺少收藏条目 ID，暂时无法移出") }
            return
        }
        val source = repository ?: run {
            _state.update { it.copy(message = "数据服务尚未就绪") }
            return
        }
        val index = snapshot.collectionItems.items.indexOfFirst { it.id == feed.id }
        _state.update { state ->
            state
                .copy(collectionItems = state.collectionItems.copy(items = state.collectionItems.items.filterNot { it.id == feed.id }))
                .withCollectionItemCount(collection.id, -1)
        }
        viewModelScope.launch {
            val result = source.removeCollectionItem(itemId)
            if (result.isFailure) {
                _state.update { state ->
                    val restored = state.collectionItems.items.toMutableList().apply {
                        add(index.coerceIn(0, size), feed)
                    }
                    state
                        .copy(
                            collectionItems = state.collectionItems.copy(items = restored),
                            message = result.exceptionOrNull()?.message ?: "移出失败，请稍后重试",
                        )
                        .withCollectionItemCount(collection.id, 1)
                }
            } else {
                _state.update { it.copy(message = "已移出收藏单") }
            }
        }
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

    private fun PagedListState<FeedCollection>.replacing(
        id: String,
        title: String,
        description: String,
        isOpen: Boolean,
    ): PagedListState<FeedCollection> = copy(
        items = items.map { if (it.id == id) it.copy(title = title, description = description, isOpen = isOpen) else it },
    )

    /** 收藏单里的内容数量随移出/回滚同步更新，列表与详情共用同一个收藏单对象。 */
    private fun FavoritesUiState.withCollectionItemCount(collectionId: String, delta: Int): FavoritesUiState {
        fun FeedCollection.adjusted() = if (id == collectionId) copy(itemCount = (itemCount + delta).coerceAtLeast(0)) else this
        return copy(
            collections = collections.copy(items = collections.items.map { it.adjusted() }),
            openedCollection = openedCollection?.adjusted(),
        )
    }

    private fun FeedCollection.updated(
        id: String,
        title: String,
        description: String,
        isOpen: Boolean,
    ): FeedCollection =
        if (this.id == id) copy(title = title, description = description, isOpen = isOpen) else this

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
