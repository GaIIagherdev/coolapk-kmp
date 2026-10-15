package app.coolapk.kmp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coolapk.kmp.core.FeedItem
import com.coolapk.kmp.core.ProductRating
import com.coolapk.kmp.core.ProductRatingChart
import com.coolapk.kmp.core.RatingChartPoint

/** 想要 / 关注 操作条：两个等宽按钮，已激活时为实心，未激活为描边。 */
@Composable
internal fun ProductActionBar(
    wish: Boolean,
    follow: Boolean,
    onWish: () -> Unit,
    onFollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProductToggleButton(active = wish, activeLabel = "已想要", idleLabel = "想要", onClick = onWish, modifier = Modifier.weight(1f))
        ProductToggleButton(active = follow, activeLabel = "已关注", idleLabel = "关注", onClick = onFollow, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ProductToggleButton(
    active: Boolean,
    activeLabel: String,
    idleLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (active) {
        Button(onClick = onClick, modifier = modifier.heightIn(min = 48.dp)) { Text(activeLabel) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp)) { Text(idleLabel) }
    }
}

/** 评分趋势：周期切换 + 折线。趋势为空时整块隐藏由调用方决定。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProductRatingChartView(
    chart: ProductRatingChart,
    period: String,
    onPeriod: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val periods = listOf("day" to "日", "week" to "周", "month" to "月")
    val points = chart.series(period)
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            periods.forEachIndexed { index, (key, label) ->
                SegmentedButton(
                    selected = period == key,
                    onClick = { onPeriod(key) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = periods.size),
                    label = { Text(label) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (points.size < 2) {
            Text(
                "数据点不足，暂无法绘制趋势",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val lineColor = MaterialTheme.colorScheme.primary
            val gridColor = MaterialTheme.colorScheme.outlineVariant
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .semantics {
                        contentDescription = "评分趋势，共 ${points.size} 个数据点，最新均分 ${"%.1f".format(points.last().score)}"
                    },
            ) {
                val step = size.width / (points.size - 1)
                // 评分范围固定为 0–10，便于不同周期之间对比
                fun yFor(score: Double): Float = (size.height * (1 - (score / 10.0).toFloat())).toFloat()
                listOf(0.0, 5.0, 10.0).forEach { mark ->
                    drawLine(gridColor, Offset(0f, yFor(mark)), Offset(size.width, yFor(mark)), strokeWidth = 1.dp.toPx())
                }
                val path = Path()
                points.forEachIndexed { index, point ->
                    val offset = Offset(step * index, yFor(point.score))
                    if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
                }
                drawPath(path, lineColor, style = Stroke(width = 2.5.dp.toPx()))
                points.forEachIndexed { index, point ->
                    drawCircle(lineColor, radius = 3.dp.toPx(), center = Offset(step * index, yFor(point.score)))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(points.first().label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(points.last().label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 点评列表页：趋势图、星级筛选、写点评入口，列表为分隔线式。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProductRatingsTab(
    chart: ProductRatingChart?,
    chartPeriod: String,
    onChartPeriod: (String) -> Unit,
    ratings: ProductPagedState<ProductRating>,
    selectedStar: Int,
    onStar: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onWrite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        chart?.let { trend ->
            item(key = "chart") {
                Column(Modifier.padding(top = 8.dp)) {
                    ProductRatingChartView(trend, chartPeriod, onChartPeriod)
                }
            }
        }
        item(key = "filter") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "全部", 5 to "5星", 4 to "4星", 3 to "3星", 2 to "2星", 1 to "1星").forEach { (star, label) ->
                        val selected = selectedStar == star
                        SegmentedChip(label = label, selected = selected) { onStar(star) }
                    }
                }
                Button(onClick = onWrite, modifier = Modifier.heightIn(min = 48.dp)) { Text("写点评") }
            }
        }
        if (ratings.items.isEmpty() && ratings.loading) {
            item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    ExpressiveLoadingIndicator()
                }
            }
        }
        itemsIndexed(ratings.items, key = { _, item -> "rating-${item.id}" }) { index, rating ->
            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            ProductRatingItem(rating)
        }
        ratings.error?.let { message ->
            item(key = "rating-error") {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text("重试") }
                }
            }
        }
        if (!ratings.loading && ratings.page > 0 && ratings.items.isEmpty() && ratings.error == null) {
            item(key = "rating-empty") {
                Text(
                    "还没有点评",
                    Modifier.fillMaxWidth().padding(32.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (ratings.items.isNotEmpty() && ratings.hasMore && !ratings.loading) {
            item(key = "rating-more") {
                TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("加载更多")
                }
            }
        }
    }
}

@Composable
private fun SegmentedChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        modifier = Modifier.heightIn(min = 48.dp),
    )
}

@Composable
private fun ProductRatingItem(rating: ProductRating) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(rating.avatarUrl, rating.username, 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(rating.username, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(rating.device, rating.time).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            rating.score?.let { StarRow(it) }
        }
        if (rating.message.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(rating.message, style = MaterialTheme.typography.bodyMedium)
        }
        if (rating.pics.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "共 ${rating.pics.size} 张图片",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StarRow(score: Int) {
    Row(
        Modifier.semantics { contentDescription = "$score 星" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(5) { index ->
            Icon(
                if (index < score) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

/** 写点评：1–5 星、可选的“我已购买”、正文。 */
@Composable
internal fun ProductRatingDialog(
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (score: Int, message: String, buyStatus: Boolean) -> Unit,
) {
    var score by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf("") }
    var bought by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("写点评") },
        text = {
            Column {
                Row {
                    (1..5).forEach { star ->
                        IconButton(onClick = { score = star }, modifier = Modifier.size(48.dp)) {
                            Icon(
                                if (star <= score) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                contentDescription = "$star 星",
                                tint = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it.take(1000) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    placeholder = { Text("说说你的使用感受（最多 1000 字）") },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = bought, onCheckedChange = { bought = it })
                    Text("我已购买")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(score, message.trim(), bought) },
                enabled = !submitting && score in 1..5 && message.isNotBlank(),
            ) { Text(if (submitting) "发布中" else "发布") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) { Text("取消") }
        },
    )
}

/** 讨论列表：产品下的动态（点评以外的讨论），复用动态卡片。 */
@Composable
internal fun ProductDiscussTab(
    discussions: ProductPagedState<FeedItem>,
    likeOverrides: Map<String, Boolean>,
    onOpenFeed: (FeedItem) -> Unit,
    onLike: (FeedItem) -> Unit,
    onOpenImage: (List<String>, Int) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (discussions.items.isEmpty() && discussions.loading) {
            item(key = "discuss-loading") {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    ExpressiveLoadingIndicator()
                }
            }
        }
        itemsIndexed(discussions.items, key = { _, item -> "discuss-${item.id}" }) { index, feed ->
            FeedCard(
                item = feed,
                liked = likeOverrides[feed.id] ?: feed.isLiked,
                onLike = { onLike(feed) },
                onOpenDetail = { onOpenFeed(feed) },
                onReply = { onOpenFeed(feed) },
                onOpenImage = onOpenImage,
                dividerAbove = index > 0,
            )
        }
        discussions.error?.let { message ->
            item(key = "discuss-error") {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text("重试") }
                }
            }
        }
        if (!discussions.loading && discussions.page > 0 && discussions.items.isEmpty() && discussions.error == null) {
            item(key = "discuss-empty") {
                Text(
                    "还没有讨论",
                    Modifier.fillMaxWidth().padding(32.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (discussions.items.isNotEmpty() && discussions.hasMore && !discussions.loading) {
            item(key = "discuss-more") {
                TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("加载更多")
                }
            }
        }
    }
}
