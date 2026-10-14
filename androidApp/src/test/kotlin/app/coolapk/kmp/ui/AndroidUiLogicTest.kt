package app.coolapk.kmp.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidUiLogicTest {
    @Test
    fun commentFocusRunsOnlyAfterFirstPageLoads() {
        val gate = CommentFocusGate()

        assertFalse(gate.shouldFocus(requested = true, activeFeed = false, repliesLoaded = true, threadOpen = false))
        assertFalse(gate.shouldFocus(requested = true, activeFeed = true, repliesLoaded = false, threadOpen = false))
        assertFalse(gate.shouldFocus(requested = true, activeFeed = true, repliesLoaded = true, threadOpen = true))
        assertTrue(gate.shouldFocus(requested = true, activeFeed = true, repliesLoaded = true, threadOpen = false))
        assertFalse(gate.shouldFocus(requested = true, activeFeed = true, repliesLoaded = true, threadOpen = false))
    }

    @Test
    fun commentFocusDoesNotRunWithoutCommentEntry() {
        val gate = CommentFocusGate()

        assertFalse(gate.shouldFocus(requested = false, activeFeed = true, repliesLoaded = true, threadOpen = false))
    }

    @Test
    fun selectorHashRouteOpensOfficialWebsite() {
        val route = "#/feed/digestList?filterId=82"

        assertTrue(isDiscoveryCardActionable(route))
        assertEquals("https://www.coolapk.com/$route", discoveryBrowserUrl(route))
    }

    @Test
    fun unsupportedCardIsNotActionable() {
        assertFalse(isDiscoveryCardActionable("coolmarket://unknown"))
        assertFalse(isDiscoveryCardActionable(""))
        assertEquals(null, discoveryBrowserUrl("//other.example/path"))
    }

    @Test
    fun imageViewerSelectionKeepsTappedImageAndRejectsInvalidIndexes() {
        val urls = mutableListOf("one.jpg", "two.jpg")
        val selection = ImageViewerSelection.create(urls, 1)

        assertEquals(1, selection?.initialIndex)
        assertEquals(listOf("one.jpg", "two.jpg"), selection?.urls)
        urls[1] = "changed.jpg"
        assertEquals("two.jpg", selection?.urls?.get(1))
        assertEquals(null, ImageViewerSelection.create(emptyList(), 0))
        assertEquals(null, ImageViewerSelection.create(listOf("one.jpg"), 1))
    }
}
