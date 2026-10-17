package com.coolapk.kmp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CollectionJsonParserTest {
    private val parser = CollectionJsonParser()

    @Test
    fun readsCollectionRowsWithCountsAndFlags() {
        val page = parser.parsePage(
            """{"code":200,"data":[
            {"id":"11","title":"数码好物","description":"攒机清单","cover":"/pic/a.png",
             "itemNum":"23","favnum":5,"follownum":"2","isOpen":"0","defaultCollected":"1","uid":"7"},
            {"entityId":"12","title":"待看","isOpen":1}
            ]}""",
        )
        assertEquals(2, page.items.size)
        val first = page.items[0]
        assertEquals("11", first.id)
        assertEquals("数码好物", first.title)
        assertEquals("https://image.coolapk.com/pic/a.png", first.coverUrl)
        assertEquals(23, first.itemCount)
        assertEquals(5, first.likeCount)
        assertEquals(2, first.followCount)
        assertFalse(first.isOpen)
        assertTrue(first.isDefault)
        assertEquals("7", first.ownerUid)
        assertEquals("12", page.items[1].id)
        assertTrue(page.items[1].isOpen)
        assertFalse(page.items[1].isDefault)
        assertNull(page.items[1].coverUrl)
        assertTrue(page.hasMore)
    }

    @Test
    fun skipsRowsWithoutIdOrTitleInsteadOfDroppingPage() {
        val page = parser.parsePage(
            """{"code":200,"data":[{"title":"无 ID"},{"id":"13"},{"id":"14","title":"正常"}]}""",
        )
        assertEquals(listOf("14"), page.items.map { it.id })
    }

    @Test
    fun emptyListStopsPagination() {
        val page = parser.parsePage("""{"code":200,"data":[]}""")
        assertTrue(page.items.isEmpty())
        assertFalse(page.hasMore)
    }

    @Test
    fun detailFallsBackToRequestedIdWhenPayloadOmitsIt() {
        val detail = parser.parseDetail(
            """{"code":200,"data":{"title":"旧机回收","itemNum":3}}""",
            collectionId = "21",
        )
        assertEquals("21", detail?.id)
        assertEquals("旧机回收", detail?.title)
        assertEquals(3, detail?.itemCount)
    }

    @Test
    fun parsesCollectionStatusWithDefaultAndCollectedFlags() {
        val options = parser.parseStatus(
            """{"code":200,"data":[
            {"id":"default-1","title":"默认收藏单","defaultCollected":1,"isBeCollected":"1"},
            {"collectionId":"c2","title":"旅行","isDefault":"0"}
            ]}""",
        )

        assertEquals(listOf("default-1", "c2"), options.map { it.id })
        assertTrue(options[0].isDefault)
        assertTrue(options[0].isCollected)
        assertFalse(options[1].isDefault)
        assertFalse(options[1].isCollected)
    }

    @Test
    fun parsesCollectionStatusWrappedInEntities() {
        val options = parser.parseStatus(
            """{"code":200,"data":{"entities":[{"entityId":"c3","title":"待看","defaultCollected":true}]}}""",
        )
        assertEquals("c3", options.single().id)
        assertTrue(options.single().isDefault)
    }

    @Test
    fun emptyCollectionStatusIsNotAFailure() {
        assertTrue(parser.parseStatus("""{"code":200,"data":[]}""").isEmpty())
        assertTrue(parser.parseStatus("""{"code":200}""").isEmpty())
    }
}
