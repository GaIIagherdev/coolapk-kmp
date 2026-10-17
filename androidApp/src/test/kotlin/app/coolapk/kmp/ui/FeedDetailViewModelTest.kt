package app.coolapk.kmp.ui

import com.coolapk.kmp.core.CaptchaChallenge
import com.coolapk.kmp.core.CoolapkGateway
import com.coolapk.kmp.core.FeedCollectionOption
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
import org.junit.Assert.assertFalse
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
    }

    @Test
    fun dismissingCaptchaRequiresVerificationWithoutWebFallback() = runTest(dispatcher) {
        val gateway = FakeFeedDetailGateway()
        val viewModel = FeedDetailViewModel { HomeRepository(gateway) }
        viewModel.open("100", null)
        runCurrent()
        gateway.failDetail(0, captchaError())
        runCurrent()

        viewModel.dismissCaptcha()
        runCurrent()

        assertNull(viewModel.state.value.captcha)
        assertNull(viewModel.state.value.detail)
        assertEquals("需要完成人机验证后才能查看此动态", viewModel.state.value.detailError)
    }

    @Test
    fun failedTokenRetryReportsVerificationNotAccepted() = runTest(dispatcher) {
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

        assertNull(viewModel.state.value.detail)
        assertEquals("人机验证未通过，请重新验证后再试", viewModel.state.value.detailError)
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
    }

    @Test
    fun favoriteAddsToDefaultCollectionAndUnfavoriteCancelsCollected() = runTest(dispatcher) {
        val gateway = FavoriteGateway()
        val viewModel = FeedDetailViewModel { HomeRepository(gateway) }
        viewModel.open("100", null)
        runCurrent()

        viewModel.toggleFeedFavorite()
        runCurrent()
        assertEquals(true, viewModel.state.value.favoriteOverride)
        assertFalse(viewModel.state.value.favoriteToggling)
        assertEquals(listOf("100|default-1||feed|"), gateway.updates)

        viewModel.toggleFeedFavorite()
        runCurrent()
        assertEquals(false, viewModel.state.value.favoriteOverride)
        assertEquals("100||c2|feed|", gateway.updates.last())
    }

    @Test
    fun failedFavoriteRollsBackOverrideAndShowsReason() = runTest(dispatcher) {
        val gateway = FavoriteGateway().apply { failFavorite = "收藏失败" }
        val viewModel = FeedDetailViewModel { HomeRepository(gateway) }
        viewModel.open("100", null)
        runCurrent()

        viewModel.toggleFeedFavorite()
        runCurrent()

        assertNull(viewModel.state.value.favoriteOverride)
        assertFalse(viewModel.state.value.favoriteToggling)
        assertEquals("收藏失败", viewModel.state.value.interactionError)
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

    fun failDetail(index: Int, error: Throwable) {
        detailReplies[index].completeExceptionally(error)
    }

    fun completeToken(index: Int, item: FeedItem) {
        tokenReplies[index].complete(item)
    }

    fun failToken(index: Int, error: Throwable) {
        tokenReplies[index].completeExceptionally(error)
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

private class FavoriteGateway : CoolapkGateway {
    var failFavorite: String? = null
    val updates = mutableListOf<String>()

    override suspend fun getFeedDetail(feedId: String): FeedItem =
        FeedItem(feedId, null, "正文", "作者", null, null, null, 0, 0, emptyList())

    override suspend fun getFeedReplies(
        feedId: String,
        page: Int,
        sort: ReplySort,
        authorOnly: Boolean,
    ): ReplyPage = ReplyPage(emptyList(), hasMore = false)

    override suspend fun getFeedCollectionStatus(feedId: String): List<FeedCollectionOption> = listOf(
        FeedCollectionOption("default-1", "默认收藏单", isDefault = true, isCollected = false),
        FeedCollectionOption("c2", "旅行", isDefault = false, isCollected = true),
    )

    override suspend fun updateCollectionItem(
        targetId: String,
        collectionIds: String,
        cancelIds: String,
        feedType: String,
        trace: String,
    ): Result<Unit> {
        failFavorite?.let { return Result.failure(IllegalStateException(it)) }
        updates += "$targetId|$collectionIds|$cancelIds|$feedType|$trace"
        return Result.success(Unit)
    }

    override suspend fun getSessionState() = SessionState(loggedIn = true)

    override suspend fun getHomePage(page: Int, firstItem: String?, lastItem: String?) =
        HomePage(emptyList())

    override fun setSessionCookie(cookie: String) = Unit
}
