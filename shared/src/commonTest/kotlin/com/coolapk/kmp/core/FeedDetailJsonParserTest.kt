package com.coolapk.kmp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeedDetailJsonParserTest {
    private val parser = FeedDetailJsonParser()

    @Test
    fun parsesDetailWithWrappedFeedAndPreservesImages() {
        val detail = parser.parseDetail(
            """
            {"code":200,"data":{"feed":{"id":"42","entityType":"feed","username":"酷友",
              "message":"完整动态","picArr":["http://image.coolapk.com/feed/1.jpg"],
              "likenum":3,"replynum":8}}}
            """.trimIndent(),
        )

        assertEquals("42", detail.id)
        assertEquals("完整动态", detail.message)
        assertEquals(listOf("https://image.coolapk.com/feed/1.jpg"), detail.imageUrls)
        assertEquals(8, detail.replyCount)
    }

    @Test
    fun rejectsDetailWithoutFeed() {
        assertFailsWith<IllegalStateException> {
            parser.parseDetail("""{"code":200,"data":{"title":"占位卡"}}""")
        }
    }

    @Test
    fun parsesNestedArticleWithoutBody() {
        val detail = parser.parseDetail(
            """{"data":{"detail":{"id":"article-1","entityType":"article","title":"图文标题"}}}""",
        )

        assertEquals("article-1", detail.id)
        assertEquals("图文标题", detail.title)
    }

    @Test
    fun parsesRepliesAndNestedPreviewWithoutTreatingDividerAsReply() {
        val page = parser.parseReplies(
            """
            {"code":200,"data":[
              {"entityType":"divider","title":"评论"},
              {"id":"reply-1","fid":"feed-1","username":"甲","userAvatar":"avatar/a.jpg",
                "message":"一级评论","dateline":1770000000,"floor":"2","likenum":"12",
                "replyRowsCount":3,"isFeedAuthor":1,"userAction":{"like":1},
                "picArr":["//image.coolapk.com/reply/a.jpg"],
                "replyRows":[{"id":"child-1","username":"乙","message":"楼中楼"}]}
            ]}
            """.trimIndent(),
        )

        assertEquals(1, page.items.size)
        assertEquals("reply-1", page.lastItem)
        assertTrue(page.hasMore)
        with(page.items.single()) {
            assertEquals("feed-1", feedId)
            assertEquals("甲", username)
            assertEquals("https://image.coolapk.com/avatar/a.jpg", avatarUrl)
            assertEquals("1770000000", createdAt)
            assertEquals(12, likeCount)
            assertEquals(3, replyCount)
            assertTrue(isLiked)
            assertTrue(isFeedAuthor)
            assertEquals(listOf("https://image.coolapk.com/reply/a.jpg"), imageUrls)
            assertEquals(ReplyPreview("乙", "楼中楼"), previewReplies.single())
        }
    }

    @Test
    fun readsExplicitEndAndSubReplyTargets() {
        val page = parser.parseReplies(
            """{"data":{"rows":[{"id":"child-2","rid":"parent-1","rusername":"楼主",
              "message":"回复楼主","userAction":{"like":false}}]},"hasMore":false}""",
        )

        assertFalse(page.hasMore)
        assertEquals("parent-1", page.items.single().parentId)
        assertEquals("楼主", page.items.single().replyToUsername)
        assertFalse(page.items.single().isLiked)
    }
}
