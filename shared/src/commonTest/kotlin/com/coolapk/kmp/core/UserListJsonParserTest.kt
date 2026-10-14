package com.coolapk.kmp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserListJsonParserTest {
    private val parser = UserListJsonParser()

    @Test
    fun readsFollowRowsWithAvatarAndSignatureFallbacks() {
        val page = parser.parsePage(
            """{"code":200,"data":[{"uid":"7","username":"甲","userAvatar":"http://img.example/a.png",
            "signature":"签名","isFollow":"1"},{"uid":"8","username":"乙","sign":"短句","isFans":"0"}]}""",
        )
        assertEquals(2, page.items.size)
        assertEquals("https://img.example/a.png", page.items[0].avatarUrl)
        assertEquals("签名", page.items[0].bio)
        assertTrue(page.items[0].isFollow)
        assertEquals("短句", page.items[1].bio)
        assertFalse(page.items[1].isFollow)
        assertTrue(page.hasMore)
    }

    @Test
    fun emptyListStopsPagination() {
        val page = parser.parsePage("""{"code":200,"data":[]}""")
        assertTrue(page.items.isEmpty())
        assertFalse(page.hasMore)
    }

    @Test
    fun qrImageAcceptsStringOrObjectPayload() {
        assertEquals("https://img.example/qr.png", parser.parseQrImage("""{"data":"https://img.example/qr.png"}"""))
        assertEquals("https://img.example/qr2.png", parser.parseQrImage("""{"data":{"imageUrl":"https://img.example/qr2.png"}}"""))
        assertNull(parser.parseQrImage("""{"data":{}}"""))
    }
}
