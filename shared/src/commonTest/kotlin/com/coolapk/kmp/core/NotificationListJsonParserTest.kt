package com.coolapk.kmp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class NotificationListJsonParserTest {
    private val parser = NotificationListJsonParser()

    @Test
    fun usesLikingActorInsteadOfFeedAuthor() {
        val page = parser.parsePage(
            """
            {"data":[{
              "id":"n1","username":"动态作者","likeUsername":"点赞者",
              "likeAvatar":"http://image.coolapk.com/a.png","feedInfo":{"id":"123","entityType":"feed","message":"原动态"},
              "feedTypeName":"动态","likeTime":1770000000,"isnew":1
            }],"hasMore":false}
            """.trimIndent(),
            NotificationCategory.LIKE,
        )

        val item = page.items.single()
        assertEquals("点赞者", item.actorName)
        assertEquals("https://image.coolapk.com/a.png", item.actorAvatarUrl)
        assertEquals("赞了你的动态", item.message)
        assertEquals("123", item.feedId)
        assertEquals("原动态", item.targetPreview)
        assertEquals(1, item.unreadCount)
        assertFalse(page.hasMore)
    }

    @Test
    fun preservesCommentTargetWithoutMistakingNotificationIdForFeedId() {
        val page = parser.parsePage(
            """
            {"data":[
              {"id":"999","fromUserInfo":{"username":"回复者","userAvatar":"//image.coolapk.com/u.png"},
               "note":"回复了你","message":"你好","feedInfo":{"id":"123","title":"原帖"}},
              {"id":"1000","note":"系统消息"}
            ]}
            """.trimIndent(),
            NotificationCategory.COMMENT,
        )

        assertEquals("123", page.items.first().feedId)
        assertEquals("回复者", page.items.first().actorName)
        assertEquals("https://image.coolapk.com/u.png", page.items.first().actorAvatarUrl)
        assertEquals("原帖", page.items.first().targetPreview)
        assertNull(page.items.last().feedId)
    }
}
