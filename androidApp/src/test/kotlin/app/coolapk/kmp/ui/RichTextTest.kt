package app.coolapk.kmp.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RichTextTest {
    private val noop: (String, String) -> Unit = { _, _ -> }

    @Test
    fun topicLinkKeepsTextAndIsClickable() {
        val html = """<a href="/t/懂车帝" class="topic">#懂车帝测试#</a> 好的"""
        val rich = parseRichText(html, Color.Blue, noop)
        assertEquals("#懂车帝测试# 好的", rich.text)
        val link = rich.getLinkAnnotations(0, rich.length).single()
        assertTrue(link.item is LinkAnnotation.Clickable)
    }

    @Test
    fun lineBreaksBecomeNewlinesAndOtherTagsAreRemoved() {
        val html = "第一行<br />第二行 <b>加粗</b> &amp; 结束"
        assertEquals("第一行\n第二行 加粗 & 结束", parseRichText(html, Color.Blue, noop).text)
    }

    @Test
    fun plainTextPassesThrough() {
        assertEquals("没有标签", parseRichText("没有标签", Color.Blue, noop).text)
    }
}
