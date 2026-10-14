package app.coolapk.kmp.ui

import com.coolapk.kmp.core.CoolapkGateway
import com.coolapk.kmp.core.HomeCursor
import com.coolapk.kmp.core.HomePage
import com.coolapk.kmp.core.HomeRepository
import com.coolapk.kmp.core.SearchCategory
import com.coolapk.kmp.core.SearchPage
import com.coolapk.kmp.core.SearchResult
import com.coolapk.kmp.core.SearchResultKind
import com.coolapk.kmp.core.SessionState
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
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun categorySwitchCachesResultsAndCursorPagination() = runTest(dispatcher) {
        val gateway = FakeSearchGateway()
        val viewModel = SearchViewModel { HomeRepository(gateway) }
        viewModel.search(" 手机 ")
        runCurrent()
        assertEquals("手机", gateway.requests[0].query)
        gateway.complete(0, page("feed-1", first = "f1", last = "l1"))
        runCurrent()

        viewModel.selectCategory(SearchCategory.APP)
        runCurrent()
        gateway.complete(1, page("app-1"))
        runCurrent()
        viewModel.selectCategory(SearchCategory.ALL)
        assertEquals(2, gateway.requests.size)
        assertEquals("feed-1", viewModel.state.value.activeTab.items.single().id)

        viewModel.loadMore()
        runCurrent()
        assertEquals(2, gateway.requests[2].page)
        assertEquals(HomeCursor("f1", "l1"), gateway.requests[2].cursor)
        gateway.complete(2, page("feed-2", more = false))
        advanceUntilIdle()
        assertEquals(listOf("feed-1", "feed-2"), viewModel.state.value.activeTab.items.map { it.id })
        assertFalse(viewModel.state.value.activeTab.hasMore)
    }

    @Test fun changedQueryIgnoresEarlierResponse() = runTest(dispatcher) {
        val gateway = FakeSearchGateway()
        val viewModel = SearchViewModel { HomeRepository(gateway) }
        viewModel.search("旧词")
        runCurrent()
        viewModel.search("新词")
        runCurrent()
        gateway.complete(0, page("old"))
        runCurrent()
        assertTrue(viewModel.state.value.activeTab.loading)
        gateway.complete(1, page("new"))
        advanceUntilIdle()
        assertEquals("新词", viewModel.state.value.query)
        assertEquals("new", viewModel.state.value.activeTab.items.single().id)
    }

    @Test fun askFilterInvalidatesOnlyAskTab() = runTest(dispatcher) {
        val gateway = FakeSearchGateway()
        val viewModel = SearchViewModel { HomeRepository(gateway) }
        viewModel.search("问题")
        runCurrent()
        gateway.complete(0, page("all"))
        runCurrent()
        viewModel.selectCategory(SearchCategory.ASK)
        runCurrent()
        gateway.complete(1, page("ask-all"))
        runCurrent()
        viewModel.selectAskFeedType("answer")
        runCurrent()
        assertEquals("answer", gateway.requests[2].askFeedType)
        assertEquals(SearchCategory.ASK, gateway.requests[2].category)
        assertEquals("all", viewModel.state.value.tabs[SearchCategory.ALL]?.items?.single()?.id)
        gateway.complete(2, page("ask-answer"))
        advanceUntilIdle()
        assertEquals("ask-answer", viewModel.state.value.activeTab.items.single().id)
    }

    @Test fun initialFailureCanRetry() = runTest(dispatcher) {
        val gateway = FakeSearchGateway()
        val viewModel = SearchViewModel { HomeRepository(gateway) }
        viewModel.search("测试")
        runCurrent()
        gateway.fail(0, "网络错误")
        runCurrent()
        assertEquals("网络错误", viewModel.state.value.activeTab.error)
        viewModel.retry()
        runCurrent()
        assertNull(viewModel.state.value.activeTab.error)
        gateway.complete(1, page("ok"))
        advanceUntilIdle()
        assertEquals("ok", viewModel.state.value.activeTab.items.single().id)
    }

    private fun page(id: String, first: String? = null, last: String? = null, more: Boolean = true) =
        SearchPage(listOf(SearchResult(id, SearchResultKind.FEED, id, null, null, id)), first, last, more)
}

private class FakeSearchGateway : CoolapkGateway {
    data class Request(
        val query: String,
        val category: SearchCategory,
        val page: Int,
        val cursor: HomeCursor?,
        val askFeedType: String,
    )

    val requests = mutableListOf<Request>()
    private val replies = mutableListOf<CompletableDeferred<SearchPage>>()

    override suspend fun searchByType(
        query: String,
        category: SearchCategory,
        page: Int,
        cursor: HomeCursor?,
        askFeedType: String,
    ): SearchPage {
        requests += Request(query, category, page, cursor, askFeedType)
        return CompletableDeferred<SearchPage>().also(replies::add).await()
    }

    override suspend fun getHotSearchWords(refresh: Boolean) = listOf("热门")
    override suspend fun getSessionState() = SessionState(loggedIn = false)
    override suspend fun getHomePage(page: Int, firstItem: String?, lastItem: String?) = HomePage(emptyList())
    override fun setSessionCookie(cookie: String) = Unit

    fun complete(index: Int, result: SearchPage) { replies[index].complete(result) }
    fun fail(index: Int, message: String) {
        replies[index].completeExceptionally(IllegalStateException(message))
    }
}
