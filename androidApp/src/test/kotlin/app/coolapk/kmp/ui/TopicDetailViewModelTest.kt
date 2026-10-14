package app.coolapk.kmp.ui

import com.coolapk.kmp.core.CoolapkGateway
import com.coolapk.kmp.core.FeedItem
import com.coolapk.kmp.core.HomeCursor
import com.coolapk.kmp.core.HomePage
import com.coolapk.kmp.core.HomeRepository
import com.coolapk.kmp.core.SessionState
import com.coolapk.kmp.core.TopicDetail
import com.coolapk.kmp.core.TopicFeedSort
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TopicDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun switchingTopicsIgnoresOlderResponses() = runTest(dispatcher) {
        val gateway = FakeTopicGateway()
        val viewModel = TopicDetailViewModel { HomeRepository(gateway) }

        viewModel.open("旧话题")
        runCurrent()
        viewModel.open("新话题")
        runCurrent()
        gateway.completeDetail(0, "旧话题")
        gateway.completePage(0, page("old"))
        runCurrent()
        assertEquals("新话题", viewModel.state.value.tag)
        assertNull(viewModel.state.value.detail)
        assertEquals(emptyList<String>(), viewModel.state.value.feeds.map(FeedItem::id))

        gateway.completeDetail(1, "新话题")
        gateway.completePage(1, page("new"))
        advanceUntilIdle()
        assertEquals("新话题", viewModel.state.value.detail?.title)
        assertEquals(listOf("new"), viewModel.state.value.feeds.map(FeedItem::id))
    }

    @Test
    fun loadsNextPageWithServerCursorAndSort() = runTest(dispatcher) {
        val gateway = FakeTopicGateway()
        val viewModel = TopicDetailViewModel { HomeRepository(gateway) }
        viewModel.open("摄影")
        runCurrent()
        gateway.completeDetail(0, "摄影")
        gateway.completePage(0, page("first", "cursor-first", "cursor-last"))
        runCurrent()

        viewModel.loadMore()
        runCurrent()
        assertEquals(2, gateway.requests[1].page)
        assertEquals(HomeCursor("cursor-first", "cursor-last"), gateway.requests[1].cursor)
        gateway.completePage(1, page("next", hasMore = false))
        runCurrent()
        assertEquals(listOf("first", "next"), viewModel.state.value.feeds.map(FeedItem::id))
        assertFalse(viewModel.state.value.hasMore)

        viewModel.selectSort(TopicFeedSort.HOT)
        runCurrent()
        assertEquals(TopicFeedSort.HOT, gateway.requests[2].sort)
        assertEquals(1, gateway.requests[2].page)
        assertNull(gateway.requests[2].cursor)
        gateway.completePage(2, page("hot", hasMore = false))
        advanceUntilIdle()
    }

    private fun page(id: String, first: String? = id, last: String? = id, hasMore: Boolean = true) =
        HomePage(
            items = listOf(FeedItem(id, null, "正文", "作者", null, null, null, 0, 0, emptyList())),
            firstItem = first,
            lastItem = last,
            hasMore = hasMore,
        )
}

private class FakeTopicGateway : CoolapkGateway {
    data class Request(val tag: String, val page: Int, val sort: TopicFeedSort, val cursor: HomeCursor?)

    val requests = mutableListOf<Request>()
    private val detailReplies = mutableListOf<CompletableDeferred<TopicDetail>>()
    private val pageReplies = mutableListOf<CompletableDeferred<HomePage>>()

    override suspend fun getTopicDetail(tag: String): TopicDetail =
        CompletableDeferred<TopicDetail>().also(detailReplies::add).await()

    override suspend fun getTopicFeeds(
        tag: String,
        page: Int,
        sort: TopicFeedSort,
        cursor: HomeCursor?,
    ): HomePage {
        requests += Request(tag, page, sort, cursor)
        return CompletableDeferred<HomePage>().also(pageReplies::add).await()
    }

    fun completeDetail(index: Int, title: String) {
        detailReplies[index].complete(TopicDetail(title, title, null, null, null, null, null))
    }

    fun completePage(index: Int, result: HomePage) {
        pageReplies[index].complete(result)
    }

    override suspend fun getSessionState() = SessionState(loggedIn = false)
    override suspend fun getHomePage(page: Int, firstItem: String?, lastItem: String?) =
        HomePage(emptyList())
    override fun setSessionCookie(cookie: String) = Unit
}
