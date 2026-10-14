package app.coolapk.kmp.ui

import com.coolapk.kmp.core.CoolapkGateway
import com.coolapk.kmp.core.DigitalProductDetail
import com.coolapk.kmp.core.DigitalProductDetailJsonParser
import com.coolapk.kmp.core.HomePage
import com.coolapk.kmp.core.HomeRepository
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
class DigitalProductDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadsDetailAndIgnoresDuplicateLoad() = runTest(dispatcher) {
        val gateway = FakeDigitalDetailGateway()
        val viewModel = DigitalProductDetailViewModel { HomeRepository(gateway) }

        viewModel.load(" 100 ")
        assertEquals("100", viewModel.state.value.productId)
        assertTrue(viewModel.state.value.loading)
        runCurrent()
        viewModel.load("100")
        assertEquals(listOf("100"), gateway.requestedIds)

        gateway.complete(0, product("100"))
        advanceUntilIdle()
        assertEquals("100", viewModel.state.value.detail?.id)
        assertFalse(viewModel.state.value.loading)
        assertNull(viewModel.state.value.error)

        viewModel.load("100")
        advanceUntilIdle()
        assertEquals(listOf("100"), gateway.requestedIds)
    }

    @Test
    fun retriesFailureAndClearsError() = runTest(dispatcher) {
        val gateway = FakeDigitalDetailGateway()
        val viewModel = DigitalProductDetailViewModel { HomeRepository(gateway) }

        viewModel.load("100")
        runCurrent()
        gateway.fail(0, "暂时不可用")
        runCurrent()
        assertEquals("暂时不可用", viewModel.state.value.error)
        assertFalse(viewModel.state.value.loading)

        viewModel.retry()
        assertTrue(viewModel.state.value.loading)
        assertNull(viewModel.state.value.error)
        runCurrent()
        gateway.complete(1, product("100"))
        advanceUntilIdle()

        assertEquals(listOf("100", "100"), gateway.requestedIds)
        assertEquals("100", viewModel.state.value.detail?.id)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun switchingProductsDiscardsLateResponseAndClearsOldDetail() = runTest(dispatcher) {
        val gateway = FakeDigitalDetailGateway()
        val viewModel = DigitalProductDetailViewModel { HomeRepository(gateway) }

        viewModel.load("100")
        runCurrent()
        viewModel.load("200")
        assertEquals("200", viewModel.state.value.productId)
        assertNull(viewModel.state.value.detail)
        assertTrue(viewModel.state.value.loading)
        runCurrent()

        gateway.complete(0, product("100"))
        runCurrent()
        assertNull(viewModel.state.value.detail)
        assertTrue(viewModel.state.value.loading)

        gateway.complete(1, product("200"))
        advanceUntilIdle()
        assertEquals("200", viewModel.state.value.detail?.id)
        assertFalse(viewModel.state.value.loading)
    }

    @Test
    fun switchingProductsDiscardsLateFailure() = runTest(dispatcher) {
        val gateway = FakeDigitalDetailGateway()
        val viewModel = DigitalProductDetailViewModel { HomeRepository(gateway) }

        viewModel.load("100")
        runCurrent()
        viewModel.load("200")
        runCurrent()
        gateway.fail(0, "旧产品错误")
        runCurrent()
        assertNull(viewModel.state.value.error)

        gateway.complete(1, product("200"))
        advanceUntilIdle()
        assertNull(viewModel.state.value.error)
        assertEquals("200", viewModel.state.value.detail?.id)
    }

    @Test
    fun forceRefreshKeepsCurrentDetailUntilNewResultArrives() = runTest(dispatcher) {
        val gateway = FakeDigitalDetailGateway()
        val viewModel = DigitalProductDetailViewModel { HomeRepository(gateway) }

        viewModel.load("100")
        runCurrent()
        gateway.complete(0, product("100", "旧标题"))
        runCurrent()

        viewModel.load("100", force = true)
        assertEquals("旧标题", viewModel.state.value.detail?.title)
        assertTrue(viewModel.state.value.loading)
        runCurrent()
        gateway.complete(1, product("100", "新标题"))
        advanceUntilIdle()
        assertEquals("新标题", viewModel.state.value.detail?.title)
    }

    private fun product(id: String, title: String = "产品$id"): DigitalProductDetail =
        requireNotNull(DigitalProductDetailJsonParser().parse(
            """{"data":{"id":"$id","title":"$title"}}"""
        ))
}

private class FakeDigitalDetailGateway : CoolapkGateway {
    val requestedIds = mutableListOf<String>()
    private val replies = mutableListOf<CompletableDeferred<DigitalProductDetail>>()

    override suspend fun getDigitalProductDetail(productId: String): DigitalProductDetail {
        requestedIds += productId
        return CompletableDeferred<DigitalProductDetail>().also(replies::add).await()
    }

    fun complete(index: Int, detail: DigitalProductDetail) {
        replies[index].complete(detail)
    }

    fun fail(index: Int, message: String) {
        replies[index].completeExceptionally(IllegalStateException(message))
    }

    override suspend fun getSessionState() = SessionState(loggedIn = false)

    override suspend fun getHomePage(page: Int, firstItem: String?, lastItem: String?) = HomePage(emptyList())

    override fun setSessionCookie(cookie: String) = Unit
}
