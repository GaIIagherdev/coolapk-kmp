package app.coolapk.kmp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.AnnotatedString.Builder as AnnotatedStringBuilder
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink

/** 正文链接被点按时的回调：参数为链接文字与 href，由应用根节点提供。 */
internal val LocalOpenRichLink = compositionLocalOf<(String, String) -> Unit> { { _, _ -> } }

private val LINK_REGEX = Regex("<a\\s[^>]*?href=\"([^\"]*)\"[^>]*>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val BR_REGEX = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
private val TAG_REGEX = Regex("<[^>]+>")

/**
 * 动态正文的富文本：与参考项目 renderCoolapkRichText 一致，保留 <a> 链接（话题 #...# 与网页），
 * 链接按主色显示并可点击，其它标签去掉。
 */
@Composable
internal fun rememberRichText(html: String): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    val onLink = LocalOpenRichLink.current
    return remember(html, linkColor, onLink) { parseRichText(html, linkColor, onLink) }
}

internal fun parseRichText(html: String, linkColor: Color, onLink: (String, String) -> Unit): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    LINK_REGEX.findAll(html).forEach { match ->
        appendPlainHtml(html.substring(cursor, match.range.first))
        val href = match.groupValues[1]
        val label = match.groupValues[2].stripTags()
        if (label.isEmpty()) {
            appendPlainHtml(match.value)
        } else {
            val styles = TextLinkStyles(SpanStyle(color = linkColor, fontWeight = FontWeight.Medium))
            withLink(LinkAnnotation.Clickable(tag = href, styles = styles) { onLink(label, href) }) {
                append(label)
            }
        }
        cursor = match.range.last + 1
    }
    appendPlainHtml(html.substring(cursor))
}

private fun AnnotatedStringBuilder.appendPlainHtml(segment: String) {
    if (segment.isEmpty()) return
    append(segment.replace(BR_REGEX, "\n").stripTags())
}

private fun String.stripTags(): String = replace(TAG_REGEX, "")
    .replace("&nbsp;", " ")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&amp;", "&")
