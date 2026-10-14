package app.coolapk.kmp.ui

import com.coolapk.kmp.core.CaptchaChallenge
import com.coolapk.kmp.core.CoolapkGateway
import com.coolapk.kmp.core.FeedItem
import com.coolapk.kmp.core.FeedUnavailableException
import com.coolapk.kmp.core.HomePage
import com.coolapk.kmp.core.HomeRepository
import com.coolapk.kmp.core.ReplyPage
import com.coolapk.kmp.core.ReplySort
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 覆盖"详情被验证码拦截 → 滑块换令牌重试 → 网页版兜底"这条链路。 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun captchaBlocksDetailWithoutShowingRawError() = runTest(dispatcher) {
        val gateway = FakeFeedDetailGateway()
        val viewModel = FeedDetailViewModel { HomeRepository(gateway) }

        viewModel.open("100", null)
        runCurrent()
        gateway.failDetail(0, captchaError())
        runCurrent()

        assertEquals(CaptchaChallenge("abc12345", "_v2_post_token"), viewModel.state.value.captcha)
        assertNull(viewModel.state.value.detailError)
        assertTrue(gateway.tokenRequests.isEmpty())
        assertTrue(gateway.webRequests.isEmpty())
    }

    @Test
    fun solvingCaptchaRetriesOnceWithOfficialToken() = runTest(dispatcher) {
        val gateway = FakeFeedDetailGateway()
        val viewModel = FeedDetailViewModel { HomeRepository(gateway) }
        viewModel.open("100", null)
        runCurrent()
        gateway.failDetail(0, captchaError())
        runCurrent()

        viewModel.submitCaptcha("validated-token")
        runCurrent()

        assertNull(viewModel.state.value.captcha)
        assertEquals(
            listOf(Triple("100", "NEC:abc12345:validated-token", "_v2_post_token")),
            gateway.tokenRequests,
        )

        gateway.completeToken(0, feed("100"))
        runCurrent()
        assertEquals("100", viewModel.state.value.detail?.id)
        assertNull(viewModel.state.value.detailError)
        assertTrue(gateway.webRequests.isEmpty())
    }

    @Test
    fun dismissingCaptchaFallsBackToWebDetail() = runTest(dispatcher) {
        val gateway = FakeFeedDetailGateway()
        val viewModel = FeedDetailViewModel { HomeRepository(gateway) }
        viewModel.open("100", null)
        runCurrent()
        gateway.failDetail(0, captchaError())
        runCurrent()

        viewModel.dismissCaptcha()
        runCurrent()

        assertNull(viewModel.state.value.captcha)
        assertEquals(listOf("100"), gateway.webRequests)

        gateway.completeWeb(0, feed("100"))
        runCurrent()
        assertEquals("100", viewModel.state.value.detail?.id)
        assertNull(viewModel.state.value.detailError)
    }

    @Test
    fun failedTokenRetryStillFallsBackToWebDetail() = runTest(dispatcher) {
        val gateway = FakeFeedDetailGateway()
        val viewModel = FeedDetailViewModel { HomeRepository(gateway) }
        viewModel.open("100", null)
        runCurrent()
        gateway.failDetail(0, captchaError())
        runCurrent()

        viewModel.submitCaptcha("validated-token")
        runCurrent()
        gateway.failToken(0, IllegalStateException("""{"code":403,"messageStatus":"err_request_captcha_v2"}"""))
        runCurrent()

        assertEquals(listOf("100"), gateway.webRequests)
        gateway.completeWeb(0, feed("100"))
        runCurrent()
        assertEquals("100", viewModel.state.value.detail?.id)
    }

    @Test
    fun webFallbackFailureSurfacesExplicitError() = runTest(dispatcher) {
        val gateway = FakeFeedDetailGateway()
        val viewModel = FeedDetailViewModel { HomeRepository(gateway) }
        viewModel.open("100", null)
        runCurrent()
        gateway.failDetail(0, captchaError())
        runCurrent()

        viewModel.dismissCaptcha()
        runCurrent()
        gateway.failWeb(0, IllegalStateException("网页版页面不可用"))
        runCurrent()

        val message = viewModel.state.value.detailError
        assertNotNull(message)
        assertTrue(message!!.startsWith("网页版动态也未能加载："))
    }

    @Test
    fun unavailableFeedShowsServerReasonWithoutWrapper() = runTest(dispatcher) {
        val gateway = FakeFeedDetailGateway()
        val viewModel = FeedDetailViewModel { HomeRepository(gateway) }
        viewModel.open("100", null)
        runCurrent()
        gateway.failDetail(0, captchaError())
        runCurrent()

        viewModel.dismissCaptcha()
        runCurrent()
        gateway.failWeb(0, FeedUnavailableException("作者设置为仅半年内动态可见，无法查看"))
        runCurrent()

        assertEquals("作者设置为仅半年内动态可见，无法查看", viewModel.state.value.detailError)
    }

    @Test
    fun nonCaptchaFailureKeepsServerMessage() = runTest(dispatcher) {        val gateway = FakeFeedDetailGateway()
        val viewModel = FeedDetailViewModel { HomeRepository(gateway) }

        viewModel.open("100", null)
        runCurrent()
        gateway.failDetail(0, IllegalStateException("网络连接超时"))
        runCurrent()

        assertNull(viewModel.state.value.captcha)
        assertEquals("网络连接超时", viewModel.state.value.detailError)
        assertTrue(gateway.webRequests.isEmpty())
    }

    private fun captchaError() = IllegalStateException(
        """{"code":403,"messageStatus":"err_request_captcha_v2","captchaId":"abc12345","captchaField":"_v2_post_token"}""",
    )

    private fun feed(id: String) = FeedItem(id, null, "正文", "作者", null, null, null, 0, 0, emptyList())
}

private class FakeFeedDetailGateway : CoolapkGateway {
    private val detailReplies = mutableListOf<CompletableDeferred<FeedItem>>()
    val tokenRequests = mutableListOf<Triple<String, String, String>>()
    private val tokenReplies = mutableListOf<CompletableDeferred<FeedItem>>()
    val webRequests = mutableListOf<String>()
    private val webReplies = mutableListOf<CompletableDeferred<FeedItem>>()

    override suspend fun getFeedDetail(feedId: String): FeedItem =
        CompletableDeferred<FeedItem>().also(detailReplies::add).await()

    override suspend fun getFeedDetailWithToken(
        feedId: String,
        postToken: String,
        postTokenField: String,
    ): FeedItem {
        tokenRequests += Triple(feedId, postToken, postTokenField)
        return CompletableDeferred<FeedItem>().also(tokenReplies::add).await()
    }

    override suspend fun getWebFeedDetail(feedId: String): FeedItem {
        webRequests += feedId
        return CompletableDeferred<FeedItem>().also(webReplies::add).await()
    }

    fun failDetail(index: Int, error: Throwable) {
        detailReplies[index].completeExceptionally(error)
    }

    fun completeToken(index: Int, item: FeedItem) {
        tokenReplies[index].complete(item)
    }

    fun failToken(index: Int, error: Throwable) {
        tokenReplies[index].completeExceptionally(error)
    }

    fun completeWeb(index: Int, item: FeedItem) {
        webReplies[index].complete(item)
    }

    fun failWeb(index: Int, error: Throwable) {
        webReplies[index].completeExceptionally(error)
    }

    override suspend fun getFeedReplies(
        feedId: String,
        page: Int,
        sort: ReplySort,
        authorOnly: Boolean,
    ): ReplyPage = ReplyPage(emptyList(), hasMore = false)

    override suspend fun getSessionState() = SessionState(loggedIn = false)

    override suspend fun getHomePage(page: Int, firstItem: String?, lastItem: String?) =
        HomePage(emptyList())

    override fun setSessionCookie(cookie: String) = Unit
}
