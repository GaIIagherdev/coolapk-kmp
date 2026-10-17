package com.coolapk.kmp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeedJsonParserTest {
    private val parser = FeedJsonParser()

    @Test
    fun parsesEntitiesAndNormalizesRelativeImageUrls() {
        val page = parser.parseHomePage(
            """
            {
              "data": [
                {
                  "entityId": "10001",
                  "title": "标题",
                  "message": "正文",
                  "userName": "测试用户",
                  "userAvatar": "avatar/1.jpg",
                  "deviceTitle": "Android",
                  "dateline": "1770000000",
                  "likenum": 12,
                  "replynum": 3,
                  "picArr": ["/feed/a.jpg", "https://image.coolapk.com/feed/b.jpg"]
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(1, page.items.size)
        assertEquals("10001", page.firstItem)
        assertEquals("10001", page.lastItem)
        assertTrue(page.hasMore)
        with(page.items.single()) {
            assertEquals("标题", title)
            assertEquals("正文", message)
            assertEquals("测试用户", username)
            assertEquals("https://image.coolapk.com/avatar/1.jpg", avatarUrl)
            assertEquals(12, likeCount)
            assertEquals(3, replyCount)
            assertEquals(
                listOf(
                    "https://image.coolapk.com/feed/a.jpg",
                    "https://image.coolapk.com/feed/b.jpg",
                ),
                imageUrls,
            )
        }
    }

    @Test
    fun readsRustCleanedFeedFieldsAndKeepsEntityCursorSeparateFromFeedId() {
        val page = parser.parseHomePage(
            """
            {
              "code": 200,
              "data": [
                {
                  "id": "feed-42",
                  "entityId": "cursor-42",
                  "entityType": "feed_reply",
                  "username": "酷友",
                  "message": "正文",
                  "userAvatar": "//image.coolapk.com/avatar/42.jpg",
                  "userInfo": {"uid": "user-42"},
                  "pics": ["feed/a.jpg", "https://image.coolapk.com/feed/b.jpg"],
                  "likenum": "12",
                  "replynum": 4,
                  "userAction": {"like": 1}
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals("feed-42", page.items.single().id)
        assertEquals("cursor-42", page.firstItem)
        assertEquals("cursor-42", page.lastItem)
        assertEquals("https://image.coolapk.com/avatar/42.jpg", page.items.single().avatarUrl)
        assertEquals(12, page.items.single().likeCount)
        assertEquals(4, page.items.single().replyCount)
        assertEquals("user-42", page.items.single().uid)
        assertTrue(page.items.single().isLiked)
        assertEquals(
            listOf(
                "https://image.coolapk.com/feed/a.jpg",
                "https://image.coolapk.com/feed/b.jpg",
            ),
            page.items.single().imageUrls,
        )
    }

    @Test
    fun upgradesLegacyCoolapkImageUrls() {
        val page = parser.parseHomePage(
            """{"data":[{"id":"feed-1","message":"图","userAvatar":"http://image.coolapk.com/avatar/a.jpg","pic":"http://image.coolapk.com/feed/a.jpg"}]}""",
        )

        assertEquals("https://image.coolapk.com/avatar/a.jpg", page.items.single().avatarUrl)
        assertEquals(listOf("https://image.coolapk.com/feed/a.jpg"), page.items.single().imageUrls)
    }

    @Test
    fun supportsWrappedEntityArrays() {
        val page = parser.parseHomePage(
            """
            {
              "data": [
                [
                  {"id": "a", "messageRaw": "第一条"},
                  {"id": "b", "message": "第二条"}
                ]
              ]
            }
            """.trimIndent(),
        )

        assertEquals(listOf("a", "b"), page.items.map(FeedItem::id))
        assertEquals("第一条", page.items[0].message)
        assertEquals("第二条", page.items[1].message)
    }

    @Test
    fun expandsCardContainersWithoutRenderingCardsAsFeeds() {
        val page = parser.parseHomePage(
            """
            {
              "data": [
                {
                  "entityId": "card-1",
                  "entityType": "card",
                  "title": "分组",
                  "entities": [
                    {"entityId": "feed-1", "entityType": "feed", "message": "动态一"},
                    {
                      "entityId": "card-2",
                      "entityType": "card",
                      "entities": [
                        {"entityId": "feed-2", "entityType": "feed", "message": "动态二"}
                      ]
                    }
                  ]
                },
                {"entityId": "feed-3", "entityType": "feed", "message": "动态三"}
              ]
            }
            """.trimIndent(),
        )

        assertEquals(listOf("feed-1", "feed-2", "feed-3"), page.items.map(FeedItem::id))
        assertEquals("card-1", page.firstItem)
        assertEquals("feed-3", page.lastItem)
    }

    @Test
    fun usesExplicitNestedPaginationMetadataBeforeEntityCursors() {
        val page = parser.parseHomePage(
            """
            {
              "data": {
                "list": [{"id":"feed-id","entityId":"row-id","entityType":"feed","message":"正文"}],
                "pageInfo": {"first_item":"server-first","last_item":"server-last","has_more":0}
              }
            }
            """.trimIndent(),
        )

        assertEquals(listOf("feed-id"), page.items.map(FeedItem::id))
        assertEquals("server-first", page.firstItem)
        assertEquals("server-last", page.lastItem)
        assertEquals(false, page.hasMore)
    }

    @Test
    fun keepsPaginationOpenForNonFeedRows() {
        val page = parser.parseHomePage(
            """{"data":[{"entityId":"card-last","entityType":"card","title":"分组"}]}""",
        )

        assertTrue(page.items.isEmpty())
        assertEquals("card-last", page.firstItem)
        assertEquals("card-last", page.lastItem)
        assertTrue(page.hasMore)
    }

    @Test
    fun returnsEmptyPageWhenPayloadIsMissing() {
        val page = parser.parseHomePage("""{"message":"ok"}""")

        assertTrue(page.items.isEmpty())
        assertEquals(null, page.firstItem)
        assertEquals(null, page.lastItem)
        assertEquals(false, page.hasMore)
    }

    @Test
    fun readsCollectionItemIdFromNestedCollectionItem() {
        val page = parser.parseHomePage(
            """
            {
              "data": [
                {
                  "id": "feed-1",
                  "username": "酷友",
                  "message": "正文",
                  "collection_item_info": { "id": "item-9" }
                },
                {
                  "id": "feed-2",
                  "username": "酷友",
                  "message": "正文",
                  "collectionItemId": "item-10"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(listOf("item-9", "item-10"), page.items.map(FeedItem::collectionItemId))
    }

    @Test
    fun readsFavoriteStateAndTypeFromFeedFields() {
        val page = parser.parseHomePage(
            """
            {"data":[
              {"id":"1","username":"酷友","message":"正文","userAction":{"collect":"1"},"feedType":"feed","trace":"t1"},
              {"id":"2","username":"酷友","message":"正文","userAction":{"favorite":0}}
            ]}
            """.trimIndent(),
        )

        assertTrue(page.items[0].isFavorited)
        assertEquals("feed", page.items[0].feedType)
        assertEquals("t1", page.items[0].trace)
        assertFalse(page.items[1].isFavorited)
    }
}

class AppAndProfileJsonParserTest {
    @Test
    fun parsesApplicationRowsAndProfileStats() {
        val apps = AppJsonParser().parsePage(
            """
            {"data":[{"packageName":"app.demo","appName":"演示应用","logo":"logo.png","versionName":"1.2.3"}]}
            """.trimIndent(),
        )
        assertEquals(1, apps.items.size)
        assertEquals("app.demo", apps.items.single().packageName)
        assertEquals("https://image.coolapk.com/logo.png", apps.items.single().iconUrl)

        val profile = ProfileJsonParser().parse(
            """
            {"data":{"uid":"42","username":"测试用户","avatar":"avatar.png","feednum":12,"follow":8,"fans":99}}
            """.trimIndent(),
        )
        requireNotNull(profile)
        assertEquals("42", profile.uid)
        assertEquals(12, profile.feedCount)
        assertEquals(99, profile.followerCount)
    }

    @Test
    fun profileFeedCountUsesOfficialAliasAndStaysUnknownWhenMissing() {
        val withFeed = ProfileJsonParser().parse(
            """{"data":{"uid":"42","username":"测试用户","feed":17}}""",
        )
        val withoutFeed = ProfileJsonParser().parse(
            """{"data":{"uid":"42","username":"测试用户"}}""",
        )

        assertEquals(17, withFeed?.feedCount)
        assertEquals(null, withoutFeed?.feedCount)
    }

    @Test
    fun parsesRustCleanedAppFieldsAndExplicitEndOfPage() {
        val apps = AppJsonParser().parsePage(
            """
            {
              "code": 200,
              "data": [
                {
                  "id": "apk-42",
                  "entityType": "apk",
                  "packageName": "app.demo",
                  "title": "演示应用",
                  "icon": "http://pp.myapp.com/ma_icon/0/icon_10910/256",
                  "description": "应用简介",
                  "version": "2.0",
                  "score": "8.5",
                  "downCountFormatted": "12万"
                }
              ],
              "hasMore": false
            }
            """.trimIndent(),
        )

        assertEquals(false, apps.hasMore)
        with(apps.items.single()) {
            assertEquals("apk-42", id)
            assertEquals("演示应用", name)
            assertEquals("app.demo", packageName)
            assertEquals("https://pp.myapp.com/ma_icon/0/icon_10910/256", iconUrl)
            assertEquals("应用简介", summary)
            assertEquals("2.0", version)
            assertEquals(8.5, score)
            assertEquals("12万", downloadCount)
        }
    }

    @Test
    fun parsesOfficialProfileFieldAliases() {
        val profile = ProfileJsonParser().parse(
            """
            {
              "data": {
                "uid": 42,
                "displayUserName": "测试用户",
                "userAvatar": "avatar/42.jpg",
                "signature": "个人简介",
                "feedNum": "12",
                "followNum": 8,
                "fansNum": 99
              }
            }
            """.trimIndent(),
        )

        requireNotNull(profile)
        assertEquals("42", profile.uid)
        assertEquals("测试用户", profile.username)
        assertEquals("https://image.coolapk.com/avatar/42.jpg", profile.avatarUrl)
        assertEquals("个人简介", profile.bio)
        assertEquals(12, profile.feedCount)
        assertEquals(8, profile.followingCount)
        assertEquals(99, profile.followerCount)
    }

    @Test
    fun parsesNotificationCountFromNestedData() {
        val summary = NotificationJsonParser().parse("""{"data":{"count":7}}""")
        assertEquals(7, summary.total)
    }

    @Test
    fun prefersV18NotificationBadgeAndSupportsCategoryOnlyResponse() {
        val parser = NotificationJsonParser()

        assertEquals(
            8,
            parser.parse("""{"data":{"badge_v18":"8","count":1,"commentme":2}}""").total,
        )
        assertEquals(
            6,
            parser.parse("""{"data":{"commentme":2,"atme":"1","feedlike":3}}""").total,
        )
        assertEquals(5, parser.parse("""{"code":200,"data":5}""").total)
    }
}
