package app.coolapk.kmp.ui

import com.coolapk.kmp.core.CoolapkGateway
import com.coolapk.kmp.core.FeedCollection
import com.coolapk.kmp.core.FeedCollectionPage
import com.coolapk.kmp.core.FeedItem
import com.coolapk.kmp.core.HomeCursor
import com.coolapk.kmp.core.HomePage
import com.coolapk.kmp.core.HomeRepository
import com.coolapk.kmp.core.SessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun blankTitleIsRejectedBeforeRequest() = runTest(dispatcher) {
        val gateway = FakeCollectionGateway()
        val viewModel = FavoritesViewModel { HomeRepository(gateway) }
        runCurrent()

        viewModel.openCreateEditor()
        viewModel.updateDraftTitle("   ")
        viewModel.submitEditor()
        runCurrent()

        assertEquals("收藏单标题不能为空", viewModel.state.value.editorError)
        assertEquals(emptyList<Pair<String, String>>(), gateway.created)
        assertFalse(viewModel.state.value.submitting)
    }

    @Test fun createSendsTrimmedFieldsAndReloadsList() = runTest(dispatcher) {
        val gateway = FakeCollectionGateway()
        val viewModel = FavoritesViewModel { HomeRepository(gateway) }
        runCurrent()

        viewModel.openCreateEditor()
        viewModel.updateDraftTitle("  旅行记录 ")
        viewModel.updateDraftDescription(" 路上拍的 ")
        viewModel.setDraftOpen(false)
        viewModel.submitEditor()
        runCurrent()

        assertEquals(listOf("旅行记录" to "路上拍的"), gateway.created)
        assertEquals(false, gateway.createdOpen)
        assertEquals(listOf("new-1", "c1", "c2"), viewModel.state.value.collections.items.map(FeedCollection::id))
        assertEquals(FavoritesTab.COLLECTIONS, viewModel.state.value.tab)
        assertNull(viewModel.state.value.editor)
        assertEquals("已创建收藏单", viewModel.state.value.message)

        viewModel.consumeMessage()
        assertNull(viewModel.state.value.message)
    }

    @Test fun failedEditKeepsEditorOpenAndLeavesRowUntouched() = runTest(dispatcher) {
        val gateway = FakeCollectionGateway()
        val viewModel = FavoritesViewModel { HomeRepository(gateway) }
        runCurrent()
        viewModel.select(FavoritesTab.COLLECTIONS)
        runCurrent()

        val target = viewModel.state.value.collections.items.first()
        gateway.updateFailure = "网络不可用"
        viewModel.openEditEditor(target)
        viewModel.updateDraftTitle("新的标题")
        viewModel.submitEditor()
        runCurrent()

        assertNotNull(viewModel.state.value.editor)
        assertEquals("网络不可用", viewModel.state.value.editorError)
        assertEquals("旧标题", viewModel.state.value.collections.items.first().title)
        assertFalse(viewModel.state.value.submitting)
    }

    @Test fun editUpdatesListAndOpenedCollection() = runTest(dispatcher) {
        val gateway = FakeCollectionGateway()
        val viewModel = FavoritesViewModel { HomeRepository(gateway) }
        runCurrent()
        viewModel.select(FavoritesTab.COLLECTIONS)
        runCurrent()
        val target = viewModel.state.value.collections.items.first()
        viewModel.openCollection(target)
        runCurrent()

        viewModel.openEditEditor(target)
        viewModel.updateDraftDescription("新的简介")
        viewModel.setDraftOpen(false)
        viewModel.submitEditor()
        runCurrent()

        assertEquals("新的简介", viewModel.state.value.collections.items.first().description)
        assertFalse(viewModel.state.value.collections.items.first().isOpen)
        assertEquals("新的简介", viewModel.state.value.openedCollection?.description)
        assertEquals("已保存修改", viewModel.state.value.message)
    }

    @Test fun deleteRemovesRowAndRestoresItOnFailure() = runTest(dispatcher) {
        val gateway = FakeCollectionGateway()
        val viewModel = FavoritesViewModel { HomeRepository(gateway) }
        runCurrent()
        viewModel.select(FavoritesTab.COLLECTIONS)
        runCurrent()

        val target = viewModel.state.value.collections.items.first()
        gateway.deleteFailure = "删除失败"
        viewModel.askDelete(target)
        viewModel.confirmDelete()
        runCurrent()

        assertEquals(listOf("c1", "c2"), viewModel.state.value.collections.items.map(FeedCollection::id))
        assertEquals("删除失败", viewModel.state.value.message)

        gateway.deleteFailure = null
        viewModel.consumeMessage()
        viewModel.askDelete(target)
        viewModel.confirmDelete()
        runCurrent()

        assertEquals(listOf("c2"), viewModel.state.value.collections.items.map(FeedCollection::id))
        assertEquals("已删除收藏单", viewModel.state.value.message)
    }

    @Test fun removeFromCollectionUsesEntryIdAndUpdatesCount() = runTest(dispatcher) {
        val gateway = FakeCollectionGateway()
        val viewModel = FavoritesViewModel { HomeRepository(gateway) }
        runCurrent()
        viewModel.select(FavoritesTab.COLLECTIONS)
        runCurrent()
        viewModel.openCollection(viewModel.state.value.collections.items.first())
        runCurrent()

        viewModel.removeFromCollection(viewModel.state.value.collectionItems.items.first())
        runCurrent()

        assertEquals(listOf("item-c1"), gateway.removed)
        assertEquals(listOf("c2-item"), viewModel.state.value.collectionItems.items.map(FeedItem::collectionItemId))
        assertEquals("已移出收藏单", viewModel.state.value.message)
        assertEquals(1, viewModel.state.value.openedCollection?.itemCount)
    }

    @Test fun failedRemovalRestoresRowAndCount() = runTest(dispatcher) {
        val gateway = FakeCollectionGateway()
        val viewModel = FavoritesViewModel { HomeRepository(gateway) }
        runCurrent()
        viewModel.select(FavoritesTab.COLLECTIONS)
        runCurrent()
        viewModel.openCollection(viewModel.state.value.collections.items.first())
        runCurrent()

        gateway.removeFailure = "移出失败"
        viewModel.removeFromCollection(viewModel.state.value.collectionItems.items.first())
        runCurrent()

        assertEquals(listOf("item-c1", "c2-item"), viewModel.state.value.collectionItems.items.map(FeedItem::collectionItemId))
        assertEquals("移出失败", viewModel.state.value.message)
        assertEquals(2, viewModel.state.value.openedCollection?.itemCount)
    }

    private class FakeCollectionGateway : CoolapkGateway {
        val created = mutableListOf<Pair<String, String>>()
        var createdOpen: Boolean? = null
        var updateFailure: String? = null
        var deleteFailure: String? = null
        var removeFailure: String? = null
        val removed = mutableListOf<String>()
        private val collections = mutableListOf(
            collection("c1", "旧标题"),
            collection("c2", "另一个收藏单"),
        )
        private val collectionFeeds = listOf(
            feed("f1", "item-c1"),
            feed("f2", "c2-item"),
        )

        override suspend fun getHomePage(page: Int, firstItem: String?, lastItem: String?): HomePage =
            HomePage(emptyList(), hasMore = false)

        override suspend fun getSessionState(): SessionState = SessionState(loggedIn = true, uid = "1")

        override fun setSessionCookie(cookie: String) = Unit

        override suspend fun getFavoriteFeeds(page: Int, cursor: HomeCursor?): HomePage =
            HomePage(emptyList(), hasMore = false)

        override suspend fun getCollections(uid: String, page: Int, cursor: HomeCursor?): FeedCollectionPage =
            FeedCollectionPage(items = collections.toList(), hasMore = false)

        override suspend fun getCollectionItems(collectionId: String, page: Int, cursor: HomeCursor?): HomePage =
            HomePage(items = collectionFeeds.toList(), hasMore = false)

        override suspend fun removeCollectionItem(itemId: String): Result<Unit> {
            removeFailure?.let { return Result.failure(IllegalStateException(it)) }
            removed += itemId
            return Result.success(Unit)
        }

        override suspend fun createCollection(
            title: String,
            description: String,
            cover: String,
            isOpen: Boolean,
        ): Result<Unit> {
            created += title to description
            createdOpen = isOpen
            collections.add(0, collection("new-1", title, description, isOpen))
            return Result.success(Unit)
        }

        override suspend fun updateCollection(
            collectionId: String,
            title: String,
            description: String,
            cover: String,
            isOpen: Boolean,
        ): Result<Unit> {
            updateFailure?.let { return Result.failure(IllegalStateException(it)) }
            val index = collections.indexOfFirst { it.id == collectionId }
            if (index >= 0) {
                collections[index] = collections[index].copy(title = title, description = description, isOpen = isOpen)
            }
            return Result.success(Unit)
        }

        override suspend fun deleteCollection(collectionId: String): Result<Unit> {
            deleteFailure?.let { return Result.failure(IllegalStateException(it)) }
            collections.removeAll { it.id == collectionId }
            return Result.success(Unit)
        }

        private fun collection(id: String, title: String, description: String = "", isOpen: Boolean = true) =
            FeedCollection(
                id = id,
                title = title,
                description = description,
                coverUrl = null,
                itemCount = 2,
                followCount = 0,
                likeCount = 0,
                isOpen = isOpen,
                isDefault = false,
                ownerUid = null,
            )

        private fun feed(id: String, collectionItemId: String) = FeedItem(
            id = id,
            title = null,
            message = "正文",
            username = "酷友",
            avatarUrl = null,
            device = null,
            createdAt = null,
            likeCount = 0,
            replyCount = 0,
            imageUrls = emptyList(),
            collectionItemId = collectionItemId,
        )
    }
}
