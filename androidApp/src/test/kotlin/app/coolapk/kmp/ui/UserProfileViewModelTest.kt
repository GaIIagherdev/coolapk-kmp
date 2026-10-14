package app.coolapk.kmp.ui

import com.coolapk.kmp.core.CoolapkGateway
import com.coolapk.kmp.core.FeedItem
import com.coolapk.kmp.core.HomePage
import com.coolapk.kmp.core.HomeRepository
import com.coolapk.kmp.core.SessionState
import com.coolapk.kmp.core.UserProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserProfileViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun ownProfileLoadsRealFeedPagesAndRetriesAfterFailure() = runTest(dispatcher) {
        val gateway = FakeUserGateway()
        val model = UserProfileViewModel { HomeRepository(gateway) }
        val profile = user("42")

        model.open(null, profile)
        runCurrent()
        assertEquals(listOf("42" to 1), gateway.feedRequests.map { it.uid to it.page })
        gateway.feedRequests[0].response.complete(page("first"))
        runCurrent()
        assertEquals("first", model.state.value.feeds.single().id)

        model.loadMore()
        runCurrent()
        gateway.feedRequests[1].response.completeExceptionally(IllegalStateException("网络错误"))
        runCurrent()
        assertEquals("网络错误", model.state.value.feedsError)
        assertEquals(1, model.state.value.feedPage)

        model.retryFeeds()
        runCurrent()
        assertEquals(2, gateway.feedRequests[2].page)
        gateway.feedRequests[2].response.complete(page("second", more = false))
        advanceUntilIdle()
        assertEquals(listOf("first", "second"), model.state.value.feeds.map(FeedItem::id))
        assertFalse(model.state.value.hasMoreFeeds)
    }

    @Test fun switchingUserIgnoresStaleProfileAndFeedResponses() = runTest(dispatcher) {
        val gateway = FakeUserGateway()
        val model = UserProfileViewModel { HomeRepository(gateway) }

        model.open("1")
        runCurrent()
        gateway.profileRequests[0].response.complete(user("1"))
        runCurrent()
        assertEquals("1", gateway.feedRequests[0].uid)

        model.open("2")
        runCurrent()
        assertEquals("2", model.state.value.requestedUid)
        gateway.feedRequests[0].response.complete(page("stale"))
        runCurrent()
        assertTrue(model.state.value.feeds.isEmpty())
        gateway.profileRequests[1].response.complete(user("2"))
        runCurrent()
        gateway.feedRequests[1].response.complete(page("current", more = false))
        advanceUntilIdle()
        assertEquals("2", model.state.value.profile?.uid)
        assertEquals("current", model.state.value.feeds.single().id)
    }

    @Test fun refreshKeepsExistingContentAndRetriesTheFirstPage() = runTest(dispatcher) {
        val gateway = FakeUserGateway()
        val profile = user("42")
        gateway.myProfile = profile
        val model = UserProfileViewModel { HomeRepository(gateway) }

        model.open(null, profile)
        runCurrent()
        gateway.feedRequests[0].response.complete(page("old"))
        runCurrent()

        model.refresh()
        runCurrent()
        assertTrue(model.state.value.refreshing)
        assertEquals(profile, model.state.value.profile)
        assertEquals("old", model.state.value.feeds.single().id)
        assertEquals(1, gateway.feedRequests[1].page)

        gateway.feedRequests[1].response.completeExceptionally(IllegalStateException("刷新失败"))
        runCurrent()
        assertFalse(model.state.value.refreshing)
        assertEquals("old", model.state.value.feeds.single().id)
        assertEquals(1, model.state.value.feedsRetryPage)

        model.retryFeeds()
        runCurrent()
        assertEquals(1, gateway.feedRequests[2].page)
        gateway.feedRequests[2].response.complete(page("new", more = false))
        advanceUntilIdle()
        assertEquals("new", model.state.value.feeds.single().id)
        assertFalse(model.state.value.refreshing)
    }

    @Test fun missingOwnProfileRequestsLoginWithoutShowingAnotherUser() = runTest(dispatcher) {
        val gateway = FakeUserGateway()
        val model = UserProfileViewModel { HomeRepository(gateway) }

        model.open(null)
        advanceUntilIdle()
        assertNull(model.state.value.profile)
        assertTrue(model.state.value.loginRequired)
        assertTrue(gateway.feedRequests.isEmpty())
    }

    private fun user(uid: String) = UserProfile(uid, "用户$uid", null, null, 2, 3, 4)

    @Test fun favoritesAndLikedListsLoadOnDemandAndStayIndependent() = runTest(dispatcher) {
        val gateway = FakeUserGateway()
        val model = UserProfileViewModel { HomeRepository(gateway) }

        model.open(null, user("42"))
        runCurrent()
        gateway.feedRequests[0].response.complete(page("feed", more = false))
        runCurrent()
        assertTrue(gateway.favoriteRequests.isEmpty())
        assertTrue(gateway.likedRequests.isEmpty())

        model.openList(ProfileListKind.FAVORITES)
        runCurrent()
        assertEquals(listOf("42" to 1), gateway.favoriteRequests.map { it.uid to it.page })
        gateway.favoriteRequests[0].response.complete(page("fav", more = false))
        advanceUntilIdle()
        assertEquals(listOf("fav"), model.state.value.favorites.items.map(FeedItem::id))
        assertTrue(model.state.value.liked.items.isEmpty())

        // 已加载过的列表不会重复请求
        model.openList(ProfileListKind.FAVORITES)
        runCurrent()
        assertEquals(1, gateway.favoriteRequests.size)

        model.openList(ProfileListKind.LIKED)
        runCurrent()
        gateway.likedRequests[0].response.completeExceptionally(IllegalStateException("赞过加载失败"))
        runCurrent()
        assertEquals("赞过加载失败", model.state.value.liked.error)
        assertEquals(1, model.state.value.liked.retryPage)

        model.retryList(ProfileListKind.LIKED)
        runCurrent()
        assertEquals(1, gateway.likedRequests[1].page)
        gateway.likedRequests[1].response.complete(page("liked", more = false))
        advanceUntilIdle()
        assertEquals(listOf("liked"), model.state.value.liked.items.map(FeedItem::id))
        assertNull(model.state.value.liked.error)
        assertEquals(listOf("fav"), model.state.value.favorites.items.map(FeedItem::id))
    }

    private fun page(id: String, more: Boolean = true) = HomePage(
        items = listOf(FeedItem(id, null, id, "作者", null, null, null, 0, 0, emptyList())),
        hasMore = more,
    )
}

private class FakeUserGateway : CoolapkGateway {
    data class ProfileRequest(val uid: String, val response: CompletableDeferred<UserProfile?>)
    data class FeedRequest(val uid: String, val page: Int, val response: CompletableDeferred<HomePage>)

    val profileRequests = mutableListOf<ProfileRequest>()
    val feedRequests = mutableListOf<FeedRequest>()
    val favoriteRequests = mutableListOf<FeedRequest>()
    val likedRequests = mutableListOf<FeedRequest>()
    var myProfile: UserProfile? = null

    override suspend fun getHomePage(page: Int, firstItem: String?, lastItem: String?): HomePage =
        HomePage(emptyList())

    override suspend fun getSessionState(): SessionState = SessionState(false)

    override fun setSessionCookie(cookie: String) = Unit

    override suspend fun getProfile(): UserProfile? = myProfile

    override suspend fun getUserProfile(uid: String): UserProfile? =
        CompletableDeferred<UserProfile?>().also { profileRequests += ProfileRequest(uid, it) }.await()

    override suspend fun getUserFeeds(uid: String, page: Int): HomePage =
        CompletableDeferred<HomePage>().also { feedRequests += FeedRequest(uid, page, it) }.await()

    override suspend fun getUserFavorites(uid: String, page: Int): HomePage =
        CompletableDeferred<HomePage>().also { favoriteRequests += FeedRequest(uid, page, it) }.await()

    override suspend fun getUserLiked(uid: String, page: Int): HomePage =
        CompletableDeferred<HomePage>().also { likedRequests += FeedRequest(uid, page, it) }.await()
}
