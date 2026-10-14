package app.coolapk.kmp.ui

import com.coolapk.kmp.core.CoolapkGateway
import com.coolapk.kmp.core.HomeRepository
import com.coolapk.kmp.core.HomePage
import com.coolapk.kmp.core.SessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PublishViewModelTest {
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
    fun restoresDraftForCurrentAccountAndSavesChanges() = runTest(dispatcher) {
        val gateway = FakePublishGateway(SessionState(loggedIn = true, uid = "42", username = "tester"))
        val store = FakeDraftStore(mutableMapOf("uid:42" to "先前的草稿"))
        val viewModel = PublishViewModel({ HomeRepository(gateway) }, store)

        advanceUntilIdle()
        assertEquals("先前的草稿", viewModel.state.value.message)
        viewModel.setMessage("新的草稿")
        advanceUntilIdle()
        assertEquals("新的草稿", store.values["uid:42"])
    }

    @Test
    fun accountSwitchKeepsDraftsSeparate() = runTest(dispatcher) {
        val gateway = FakePublishGateway(SessionState(loggedIn = true, uid = "42"))
        val store = FakeDraftStore(mutableMapOf("uid:99" to "另一个账号的草稿"))
        val viewModel = PublishViewModel({ HomeRepository(gateway) }, store)

        advanceUntilIdle()
        viewModel.setMessage("原账号尚未自动保存的内容")
        gateway.session = SessionState(loggedIn = true, uid = "99")
        viewModel.refreshSessionAndDraft()
        advanceUntilIdle()

        assertEquals("原账号尚未自动保存的内容", store.values["uid:42"])
        assertEquals("另一个账号的草稿", viewModel.state.value.message)
        assertEquals("uid:99", viewModel.state.value.accountKey)
    }

    @Test
    fun saveDraftCallbackRunsAfterPersistence() = runTest(dispatcher) {
        val gateway = FakePublishGateway(SessionState(loggedIn = true, uid = "42"))
        val store = FakeDraftStore()
        val viewModel = PublishViewModel({ HomeRepository(gateway) }, store)

        advanceUntilIdle()
        viewModel.setMessage("离开前保存")
        var savedAtCallback: String? = null
        viewModel.saveDraftNow { savedAtCallback = store.values["uid:42"] }
        advanceUntilIdle()

        assertEquals("离开前保存", savedAtCallback)
    }

    @Test
    fun unauthenticatedSubmitIsRejectedWithoutCallingGateway() = runTest(dispatcher) {
        val gateway = FakePublishGateway(SessionState(loggedIn = false))
        val viewModel = PublishViewModel({ HomeRepository(gateway) }, FakeDraftStore())

        advanceUntilIdle()
        viewModel.setMessage("准备发布")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals("请先登录后再发布", viewModel.state.value.error)
        assertEquals(0, gateway.createCalls)
    }

    @Test
    fun successfulSubmitUsesTrimmedTextAndClearsDraft() = runTest(dispatcher) {
        val gateway = FakePublishGateway(SessionState(loggedIn = true, uid = "42"))
        gateway.createResult = Result.success("feed-100")
        val store = FakeDraftStore(mutableMapOf("uid:42" to "旧草稿"))
        val viewModel = PublishViewModel({ HomeRepository(gateway) }, store)

        advanceUntilIdle()
        viewModel.setMessage("  发布内容  ")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals("发布内容", gateway.lastMessage)
        assertEquals(1, gateway.createCalls)
        assertEquals("feed-100", viewModel.state.value.publishedFeedId)
        assertEquals("", store.values["uid:42"].orEmpty())
        assertEquals("", viewModel.state.value.message)
    }

    @Test
    fun accountChangeStopsSubmitBeforeNetworkMutation() = runTest(dispatcher) {
        val gateway = FakePublishGateway(SessionState(loggedIn = true, uid = "42"))
        val store = FakeDraftStore(mutableMapOf("uid:99" to "新账号草稿"))
        val viewModel = PublishViewModel({ HomeRepository(gateway) }, store)

        advanceUntilIdle()
        viewModel.setMessage("不可跨账号发布")
        gateway.session = SessionState(loggedIn = true, uid = "99")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(0, gateway.createCalls)
        assertEquals("登录状态已变化，请重新确认后再发布", viewModel.state.value.error)
        assertEquals("不可跨账号发布", store.values["uid:42"])
        assertEquals("新账号草稿", viewModel.state.value.message)
    }

    @Test
    fun captchaFailureIsConvertedToActionableMessage() = runTest(dispatcher) {
        val gateway = FakePublishGateway(SessionState(loggedIn = true, uid = "42"))
        gateway.createResult = Result.failure(IllegalStateException("err_request_captcha_v2"))
        val store = FakeDraftStore()
        val viewModel = PublishViewModel({ HomeRepository(gateway) }, store)

        advanceUntilIdle()
        viewModel.setMessage("需要验证")
        viewModel.submit()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.error.orEmpty().contains("验证码"))
        assertFalse(viewModel.state.value.notice == "发布成功")
        assertEquals("需要验证", store.values["uid:42"])
    }
}

private class FakeDraftStore(
    val values: MutableMap<String, String> = mutableMapOf(),
) : PublishDraftStore {
    override suspend fun load(accountKey: String): String = values[accountKey].orEmpty()

    override suspend fun save(accountKey: String, message: String) {
        values[accountKey] = message
    }

    override suspend fun clear(accountKey: String) {
        values.remove(accountKey)
    }
}

private class FakePublishGateway(
    var session: SessionState,
) : CoolapkGateway {
    var createResult: Result<String> = Result.success("feed-default")
    var createCalls: Int = 0
    var lastMessage: String? = null

    override suspend fun getSessionState(): SessionState = session

    override suspend fun getHomePage(page: Int, firstItem: String?, lastItem: String?): HomePage = HomePage(emptyList())

    override suspend fun createFeed(message: String): Result<String> {
        createCalls++
        lastMessage = message
        return createResult
    }

    override fun setSessionCookie(cookie: String) = Unit
}
