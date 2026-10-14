package app.coolapk.kmp.ui

import com.coolapk.kmp.core.CoolapkGateway
import com.coolapk.kmp.core.DiscoveryCard
import com.coolapk.kmp.core.DiscoveryConfig
import com.coolapk.kmp.core.DiscoveryPage
import com.coolapk.kmp.core.DiscoveryTab
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
class DiscoveryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val firstTab = DiscoveryTab("first", "关注", "/discovery/first")
    private val secondTab = DiscoveryTab("second", "热门", "/discovery/second")
    private val firstFilter = card("filter-first", "selectorLink", "/discovery/first?sort=latest")
    private val secondFilter = card("filter-second", "selectorLink", "/discovery/first?sort=popular")
    private val header = card("header", "titleCard")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun switchingTabsRestoresFilteredPageAndCursorWithoutRequestingAgain() = runTest(dispatcher) {
        val gateway = FakeDiscoveryGateway(DiscoveryConfig(listOf(firstTab, secondTab)))
        val viewModel = DiscoveryViewModel { HomeRepository(gateway) }

        viewModel.loadConfig()
        runCurrent()
        gateway.complete(0, page(listOf(header, firstFilter, secondFilter, card("base"))))
        runCurrent()

        viewModel.selectFilter(firstFilter)
        runCurrent()
        gateway.complete(1, page(listOf(card("filtered-one")), "first-1", "last-1", "context-1"))
        runCurrent()
        viewModel.loadMore()
        runCurrent()
        gateway.complete(2, page(listOf(card("filtered-two")), "first-2", "last-2", "context-2"))
        runCurrent()

        viewModel.selectTab(secondTab)
        runCurrent()
        gateway.complete(3, page(listOf(card("second-content")), "second-first", "second-last", "second-context"))
        runCurrent()

        val requestCount = gateway.requests.size
        viewModel.selectTab(firstTab)
        runCurrent()
        val restored = viewModel.state.value
        assertEquals("first", restored.selectedTab?.key)
        assertEquals(firstFilter.url, restored.selectedFilterUrl)
        assertEquals(listOf("header", "filter-first", "filter-second", "filtered-one", "filtered-two"), restored.cards.map { it.id })
        assertEquals(2, restored.page)
        assertEquals(HomeCursor("first-2", "last-2"), restored.cursor)
        assertEquals("context-2", restored.pageContext)
        assertTrue(restored.hasMore)
        assertFalse(restored.loading)
        assertEquals(requestCount, gateway.requests.size)

        viewModel.selectTab(secondTab)
        runCurrent()
        assertEquals(listOf("second-content"), viewModel.state.value.cards.map { it.id })
        assertEquals(1, viewModel.state.value.page)
        assertEquals(HomeCursor("second-first", "second-last"), viewModel.state.value.cursor)
        assertEquals("second-context", viewModel.state.value.pageContext)
        assertNull(viewModel.state.value.selectedFilterUrl)
        assertEquals(requestCount, gateway.requests.size)
    }

    @Test
    fun filterKeepsHeaderAndSelectorsRejectsOldResponseAndRetriesPagination() = runTest(dispatcher) {
        val gateway = FakeDiscoveryGateway(DiscoveryConfig(listOf(firstTab)))
        val viewModel = DiscoveryViewModel { HomeRepository(gateway) }

        viewModel.loadConfig()
        runCurrent()
        gateway.complete(0, page(listOf(header, firstFilter, secondFilter, card("original"))))
        runCurrent()

        viewModel.selectFilter(firstFilter)
        runCurrent()
        assertEquals(firstFilter.url, gateway.requests[1].tab.url)
        assertEquals(1, gateway.requests[1].page)
        assertEquals(listOf("header", "filter-first", "filter-second"), viewModel.state.value.cards.map { it.id })

        viewModel.selectFilter(secondFilter)
        runCurrent()
        gateway.complete(1, page(listOf(card("stale"))))
        runCurrent()
        assertEquals(secondFilter.url, viewModel.state.value.selectedFilterUrl)
        assertTrue(viewModel.state.value.loading)
        assertFalse(viewModel.state.value.cards.any { it.id == "stale" })

        assertEquals(secondFilter.url, gateway.requests[2].tab.url)
        gateway.complete(2, page(listOf(card("current")), "filter-first", "filter-last", "filter-context"))
        runCurrent()
        assertEquals(listOf("header", "filter-first", "filter-second", "current"), viewModel.state.value.cards.map { it.id })
        assertEquals(1, viewModel.state.value.page)

        viewModel.loadMore()
        runCurrent()
        val nextPage = gateway.requests[3]
        assertEquals(secondFilter.url, nextPage.tab.url)
        assertEquals(2, nextPage.page)
        assertEquals(HomeCursor("filter-first", "filter-last"), nextPage.cursor)
        assertEquals("filter-context", nextPage.pageContext)
        gateway.fail(3, "分页失败")
        runCurrent()
        assertEquals("分页失败", viewModel.state.value.error)
        assertEquals(1, viewModel.state.value.page)

        viewModel.loadMore()
        runCurrent()
        assertEquals(4, gateway.requests.size)
        viewModel.retry()
        runCurrent()
        assertNull(viewModel.state.value.error)
        assertEquals(nextPage.tab, gateway.requests[4].tab)
        assertEquals(nextPage.page, gateway.requests[4].page)
        assertEquals(nextPage.cursor, gateway.requests[4].cursor)
        assertEquals(nextPage.pageContext, gateway.requests[4].pageContext)
        gateway.complete(4, page(listOf(card("next")), "next-first", "next-last", "next-context", hasMore = false))
        runCurrent()
        assertEquals(listOf("header", "filter-first", "filter-second", "current", "next"), viewModel.state.value.cards.map { it.id })
        assertEquals(2, viewModel.state.value.page)
        assertFalse(viewModel.state.value.hasMore)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun retryAfterFirstFilteredPageFailsKeepsHeaderAndFilter() = runTest(dispatcher) {
        val gateway = FakeDiscoveryGateway(DiscoveryConfig(listOf(firstTab)))
        val viewModel = DiscoveryViewModel { HomeRepository(gateway) }

        viewModel.loadConfig()
        runCurrent()
        gateway.complete(0, page(listOf(header, firstFilter, secondFilter, card("original"))))
        runCurrent()

        viewModel.selectFilter(firstFilter)
        runCurrent()
        gateway.fail(1, "筛选失败")
        runCurrent()
        assertEquals("筛选失败", viewModel.state.value.error)
        assertEquals(0, viewModel.state.value.page)
        assertEquals(firstFilter.url, viewModel.state.value.selectedFilterUrl)
        assertEquals(listOf("header", "filter-first", "filter-second"), viewModel.state.value.cards.map { it.id })

        viewModel.retry()
        runCurrent()
        val retry = gateway.requests[2]
        assertEquals(firstFilter.url, retry.tab.url)
        assertEquals(1, retry.page)
        assertNull(retry.cursor)
        assertNull(retry.pageContext)
        gateway.complete(2, page(listOf(card("filtered"))))
        runCurrent()
        assertEquals(listOf("header", "filter-first", "filter-second", "filtered"), viewModel.state.value.cards.map { it.id })
        assertEquals(1, viewModel.state.value.page)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun reloadingConfigKeepsSelectedTabWhenItStillExists() = runTest(dispatcher) {
        val gateway = FakeDiscoveryGateway(DiscoveryConfig(listOf(firstTab, secondTab)))
        val viewModel = DiscoveryViewModel { HomeRepository(gateway) }

        viewModel.loadConfig()
        runCurrent()
        gateway.complete(0, page(listOf(card("first-content"))))
        runCurrent()
        viewModel.selectTab(secondTab)
        runCurrent()
        gateway.complete(1, page(listOf(card("second-content"))))
        runCurrent()

        val updatedSecond = secondTab.copy(title = "最新热门", url = "/discovery/second-updated")
        gateway.config = DiscoveryConfig(listOf(firstTab, updatedSecond), selectedKey = firstTab.key)
        viewModel.loadConfig()
        runCurrent()

        assertEquals("second", viewModel.state.value.selectedTab?.key)
        assertEquals(updatedSecond, viewModel.state.value.selectedTab)
        assertEquals(listOf(firstTab, updatedSecond), viewModel.state.value.tabs)
    }

    private fun page(
        cards: List<DiscoveryCard>,
        firstItem: String? = null,
        lastItem: String? = null,
        pageContext: String? = null,
        hasMore: Boolean = true,
    ) = DiscoveryPage(cards, firstItem, lastItem, pageContext, hasMore)

    private fun card(id: String, entityType: String? = "feed", url: String? = null) =
        DiscoveryCard(id, id, null, null, url, entityType)
}

private class FakeDiscoveryGateway(var config: DiscoveryConfig) : CoolapkGateway {
    data class Request(
        val tab: DiscoveryTab,
        val page: Int,
        val cursor: HomeCursor?,
        val pageContext: String?,
        val reply: CompletableDeferred<DiscoveryPage>,
    )

    val requests = mutableListOf<Request>()

    override suspend fun getDiscoveryConfig(): DiscoveryConfig = config

    override suspend fun getDiscoverySectionPage(
        tab: DiscoveryTab,
        page: Int,
        cursor: HomeCursor?,
        pageContext: String?,
    ): DiscoveryPage = CompletableDeferred<DiscoveryPage>().also { reply ->
        requests += Request(tab, page, cursor, pageContext, reply)
    }.await()

    fun complete(index: Int, page: DiscoveryPage) {
        requests[index].reply.complete(page)
    }

    fun fail(index: Int, message: String) {
        requests[index].reply.completeExceptionally(IllegalStateException(message))
    }

    override suspend fun getHomePage(page: Int, firstItem: String?, lastItem: String?) = HomePage(emptyList())

    override suspend fun getSessionState() = SessionState(loggedIn = false)

    override fun setSessionCookie(cookie: String) = Unit
}
