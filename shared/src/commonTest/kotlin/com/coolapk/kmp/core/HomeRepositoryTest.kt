package com.coolapk.kmp.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeRepositoryTest {
    @Test
    fun forwardsCursorToGateway() = runTest {
        val gateway = RecordingGateway()
        val repository = HomeRepository(gateway)

        val page = repository.load(
            page = 3,
            cursor = HomeCursor(firstItem = "first", lastItem = "last"),
        )

        assertEquals(3, gateway.requestedPage)
        assertEquals("first", gateway.requestedFirstItem)
        assertEquals("last", gateway.requestedLastItem)
        assertEquals(listOf("result"), page.items.map(FeedItem::id))
    }

    @Test
    fun forwardsAccessCodeAndCookieToGateway() = runTest {
        val gateway = RecordingGateway()
        val session = HomeRepository(gateway).loginWithAccessCode("one-time-code", "SESSID=candidate")

        assertEquals("one-time-code", gateway.requestedCode)
        assertEquals("SESSID=candidate", gateway.requestedCookie)
        assertEquals(SessionState(loggedIn = true, uid = "42"), session)
    }

    private class RecordingGateway : CoolapkGateway {
        var requestedPage: Int? = null
        var requestedFirstItem: String? = null
        var requestedLastItem: String? = null
        var requestedCode: String? = null
        var requestedCookie: String? = null

        override suspend fun getHomePage(
            page: Int,
            firstItem: String?,
            lastItem: String?,
        ): HomePage {
            requestedPage = page
            requestedFirstItem = firstItem
            requestedLastItem = lastItem
            return HomePage(
                items = listOf(
                    FeedItem(
                        id = "result",
                        title = null,
                        message = "message",
                        username = "user",
                        avatarUrl = null,
                        device = null,
                        createdAt = null,
                        likeCount = 0,
                        replyCount = 0,
                        imageUrls = emptyList(),
                    ),
                ),
            )
        }

        override suspend fun getSessionState(): SessionState = SessionState(loggedIn = false)

        override suspend fun loginWithAccessCode(code: String, cookie: String): SessionState {
            requestedCode = code
            requestedCookie = cookie
            return SessionState(loggedIn = true, uid = "42")
        }

        override fun setSessionCookie(cookie: String) = Unit
    }
}
