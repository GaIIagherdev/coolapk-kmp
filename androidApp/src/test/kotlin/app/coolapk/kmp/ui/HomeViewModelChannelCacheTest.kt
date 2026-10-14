package app.coolapk.kmp.ui

import com.coolapk.kmp.core.CoolapkGateway
import com.coolapk.kmp.core.FeedChannel
import com.coolapk.kmp.core.FeedItem
import com.coolapk.kmp.core.HomeCursor
import com.coolapk.kmp.core.HomePage
import com.coolapk.kmp.core.HomeRepository
import com.coolapk.kmp.core.SessionState
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelChannelCacheTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun channelsKeepIndependentItemsPagesCursorsAndHasMore() = runTest(dispatcher) {
        val gateway = FakeHomeCacheGateway()
        val viewModel = HomeViewModel { HomeRepository(gateway) }
        runCurrent()
        gateway.completeFeed(0, page("home-1", "home-first", "home-last"))
        runCurrent()

        viewModel.selectChannel(FeedChannel.HOT)
        runCurrent()
        gateway.completeFeed(1, page("hot-1", "hot-first", "hot-last"))
        runCurrent()

        viewModel.selectChannel(FeedChannel.DISCOVERY)
        runCurrent()
        gateway.completeFeed(2, page("discovery-1", "discovery-first", "discovery-last", more = false))
        runCurrent()

        viewModel.selectChannel(FeedChannel.HOME)
        assertEquals(3, gateway.feedRequests.size)
        assertPage(viewModel.state.value, listOf("home-1"), 1, HomeCursor("home-first", "home-last"), true)

        viewModel.loadMore()
        runCurrent()
        assertEquals(FakeHomeCacheGateway.FeedRequestKey(FeedChannel.HOME, 2,
            HomeCursor("home-first", "home-last")), gateway.feedRequests[3].key)
        gateway.completeFeed(3, page("home-2", "home-second", "home-end", more = false))
        runCurrent()
        assertPage(viewModel.state.value, listOf("home-1", "home-2"), 2,
            HomeCursor("home-second", "home-end"), false)

        viewModel.selectChannel(FeedChannel.HOT)
        assertEquals(4, gateway.feedRequests.size)
        assertPage(viewModel.state.value, listOf("hot-1"), 1,
            HomeCursor("hot-first", "hot-last"), true)

        viewModel.selectChannel(FeedChannel.DISCOVERY)
        assertEquals(4, gateway.feedRequests.size)
        assertPage(viewModel.state.value, listOf("discovery-1"), 1,
            HomeCursor("discovery-first", "discovery-last"), false)
        assertEquals(listOf("home-1", "home-2"),
            viewModel.state.value.channelPages[FeedChannel.HOME]?.items?.map(FeedItem::id))
    }

    @Test fun lateResponseUpdatesItsChannelWithoutReplacingVisibleChannel() = runTest(dispatcher) {
        val gateway = FakeHomeCacheGateway()
        val viewModel = HomeViewModel { HomeRepository(gateway) }
        runCurrent()
        viewModel.selectChannel(FeedChannel.HOT)
        runCurrent()

        gateway.completeFeed(0, page("late-home", "h-first", "h-last"))
        runCurrent()
        assertEquals(FeedChannel.HOT, viewModel.state.value.channel)
        assertTrue(viewModel.state.value.loading)
        assertTrue(viewModel.state.value.items.isEmpty())
        assertEquals("late-home",
            viewModel.state.value.channelPages[FeedChannel.HOME]?.items?.single()?.id)

        gateway.completeFeed(1, page("hot", "hot-first", "hot-last"))
        runCurrent()
        assertEquals("hot", viewModel.state.value.items.single().id)
        viewModel.selectChannel(FeedChannel.HOME)
        assertEquals(2, gateway.feedRequests.size)
        assertPage(viewModel.state.value, listOf("late-home"), 1,
            HomeCursor("h-first", "h-last"), true)
    }

    @Test fun searchDoesNotReplaceHomeCacheAndClearSearchIgnoresOldResponse() = runTest(dispatcher) {
        val gateway = FakeHomeCacheGateway()
        val viewModel = HomeViewModel { HomeRepository(gateway) }
        runCurrent()
        gateway.completeFeed(0, page("home", "h-first", "h-last"))
        runCurrent()

        viewModel.searchNow("  手机  ")
        runCurrent()
        assertEquals(FakeHomeCacheGateway.SearchRequestKey("手机", 1), gateway.searchRequests[0].key)
        gateway.completeSearch(0, page("search", "s-first", "s-last"))
        runCurrent()
        assertEquals("search", viewModel.state.value.items.single().id)
        assertEquals("home", viewModel.state.value.channelPages[FeedChannel.HOME]?.items?.single()?.id)

        viewModel.clearSearch()
        assertNull(viewModel.state.value.searchQuery)
        assertEquals(FeedChannel.HOME, viewModel.state.value.channel)
        assertPage(viewModel.state.value, listOf("home"), 1,
            HomeCursor("h-first", "h-last"), true)
        assertEquals(1, gateway.feedRequests.size)

        viewModel.searchNow("过期搜索")
        runCurrent()
        viewModel.clearSearch()
        gateway.completeSearch(1, page("stale-result"))
        runCurrent()
        assertEquals(listOf("home"), viewModel.state.value.items.map(FeedItem::id))
        assertEquals(listOf("home"),
            viewModel.state.value.channelPages[FeedChannel.HOME]?.items?.map(FeedItem::id))
        assertEquals(1, gateway.feedRequests.size)
    }

    @Test fun logoutClearsCachesAndInvalidatesPendingFeedAndSearch() = runTest(dispatcher) {
        val gateway = FakeHomeCacheGateway()
        val viewModel = HomeViewModel { HomeRepository(gateway) }
        runCurrent()
        gateway.completeFeed(0, page("old-home"))
        runCurrent()
        viewModel.selectChannel(FeedChannel.HOT)
        runCurrent()
        gateway.completeFeed(1, page("old-hot"))
        runCurrent()
        viewModel.selectChannel(FeedChannel.DISCOVERY)
        runCurrent()
        viewModel.searchNow("pending")
        runCurrent()

        viewModel.logout()
        assertEmptyAccountPages(viewModel.state.value)
        gateway.completeFeed(2, page("late-discovery"))
        gateway.completeSearch(0, page("late-search"))
        runCurrent()
        assertEmptyAccountPages(viewModel.state.value)

        viewModel.selectChannel(FeedChannel.HOT)
        runCurrent()
        assertEquals(4, gateway.feedRequests.size)
        gateway.completeFeed(3, page("new-hot"))
        runCurrent()
        assertEquals("new-hot", viewModel.state.value.items.single().id)
    }

    @Test fun loginClearsCachesAndInvalidatesPendingChannelResponse() = runTest(dispatcher) {
        val gateway = FakeHomeCacheGateway()
        val viewModel = HomeViewModel { HomeRepository(gateway) }
        runCurrent()
        gateway.completeFeed(0, page("old-home"))
        runCurrent()
        viewModel.selectChannel(FeedChannel.HOT)
        runCurrent()
        gateway.completeFeed(1, page("old-hot"))
        runCurrent()
        viewModel.selectChannel(FeedChannel.DISCOVERY)
        runCurrent()

        var loginError: String? = "not completed"
        viewModel.loginWithCookie("new-account") { loginError = it }
        runCurrent()
        assertNull(loginError)
        assertEmptyAccountPages(viewModel.state.value)
        gateway.completeFeed(2, page("late-discovery"))
        runCurrent()
        assertEmptyAccountPages(viewModel.state.value)

        viewModel.selectChannel(FeedChannel.HOT)
        runCurrent()
        assertEquals(4, gateway.feedRequests.size)
        gateway.completeFeed(3, page("new-account-hot"))
        runCurrent()
        assertEquals("new-account-hot", viewModel.state.value.items.single().id)
    }

    private fun assertPage(
        state: HomeUiState,
        ids: List<String>,
        page: Int,
        cursor: HomeCursor?,
        hasMore: Boolean,
    ) {
        assertEquals(ids, state.items.map(FeedItem::id))
        assertEquals(page, state.page)
        assertEquals(cursor, state.cursor)
        assertEquals(hasMore, state.hasMore)
        assertFalse(state.loading)
    }

    private fun assertEmptyAccountPages(state: HomeUiState) {
        assertEquals(FeedChannel.HOME, state.channel)
        assertNull(state.searchQuery)
        assertTrue(state.items.isEmpty())
        assertEquals(0, state.page)
        assertNull(state.cursor)
        assertTrue(state.hasMore)
        assertFalse(state.loading)
        assertTrue(state.channelPages.values.all { it.items.isEmpty() && it.page == 0 })
        assertTrue(state.searchPage.items.isEmpty())
    }

    private fun page(
        id: String,
        first: String? = null,
        last: String? = null,
        more: Boolean = true,
    ) = HomePage(
        items = listOf(FeedItem(id, null, id, "tester", null, null, null, 0, 0, emptyList())),
        firstItem = first,
        lastItem = last,
        hasMore = more,
    )
}

private class FakeHomeCacheGateway : CoolapkGateway {
    data class FeedRequestKey(val channel: FeedChannel, val page: Int, val cursor: HomeCursor?)
    data class SearchRequestKey(val query: String, val page: Int)
    data class FeedRequest(val key: FeedRequestKey, val reply: CompletableDeferred<HomePage>)
    data class SearchRequest(val key: SearchRequestKey, val reply: CompletableDeferred<HomePage>)

    val feedRequests = mutableListOf<FeedRequest>()
    val searchRequests = mutableListOf<SearchRequest>()

    override suspend fun getFeedPage(channel: FeedChannel, page: Int, cursor: HomeCursor?): HomePage {
        val request = FeedRequest(FeedRequestKey(channel, page, cursor), CompletableDeferred())
        feedRequests += request
        return request.reply.await()
    }

    override suspend fun getHomePage(page: Int, firstItem: String?, lastItem: String?): HomePage =
        getFeedPage(FeedChannel.HOME, page,
            if (firstItem != null && lastItem != null) HomeCursor(firstItem, lastItem) else null)

    override suspend fun searchFeeds(query: String, page: Int): HomePage {
        val request = SearchRequest(SearchRequestKey(query, page), CompletableDeferred())
        searchRequests += request
        return request.reply.await()
    }

    override suspend fun getSessionState() = SessionState(loggedIn = false)
    override fun setSessionCookie(cookie: String) = Unit
    override suspend fun loginWithCookie(cookie: String) =
        SessionState(loggedIn = true, uid = cookie, username = "tester")

    fun completeFeed(index: Int, page: HomePage) { feedRequests[index].reply.complete(page) }
    fun completeSearch(index: Int, page: HomePage) { searchRequests[index].reply.complete(page) }
}
