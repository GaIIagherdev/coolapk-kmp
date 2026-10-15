package app.coolapk.kmp.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.coolapk.kmp.core.AppItem
import com.coolapk.kmp.core.FeedItem

/* ------------------------------ 表面与容器 ------------------------------ */

/** 语义表面卡片：内容卡片统一使用，避免逐处配置颜色与形状。 */
@Composable
internal fun CoolapkCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    shape: Shape = MaterialTheme.shapes.medium,
    elevated: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .then(if (elevated) Modifier.shadow(1.dp, shape) else Modifier)
            .clip(shape)
            .background(containerColor)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

/** 分组列表容器：整组一张表面，首尾大圆角，中间用分隔线相连。 */
@Composable
internal fun GroupedSurface(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    content: @Composable ColumnScope.() -> Unit,
) {
    CoolapkCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = containerColor,
        shape = MaterialTheme.shapes.large,
        content = content,
    )
}

@Composable
internal fun GroupedDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
    )
}

/**
 * 分段容器中单项的形状：首项上方大圆角，末项下方大圆角，中间项为直角，
 * 使紧邻列表读作一个整体表面。
 */
internal fun groupedItemShape(index: Int, count: Int): Shape {
    val corner = 26.dp
    return RoundedCornerShape(
        topStart = if (index == 0) corner else 0.dp,
        topEnd = if (index == 0) corner else 0.dp,
        bottomStart = if (index == count - 1) corner else 0.dp,
        bottomEnd = if (index == count - 1) corner else 0.dp,
    )
}

/* ------------------------------ 区块与标签 ------------------------------ */

@Composable
internal fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    horizontalPadding: Dp = 16.dp,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = horizontalPadding, end = horizontalPadding, top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel)
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
internal fun InfoPill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.tertiaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onTertiaryContainer,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun StatColumn(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/* ------------------------------ 状态与加载 ------------------------------ */

/**
 * Expressive 形状变化加载指示器：旋转 + 呼吸缩放 + 圆角形变，
 * 不使用无限弹性/视差，服务加载语义。
 */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    // M3E 形状变化加载指示器，统一替代各页面的转圈与自绘动画
    LoadingIndicator(
        modifier = modifier.size(size),
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
internal fun CoolapkEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: ImageVector = Icons.Rounded.Inbox,
) {
    StateScaffold(
        icon = icon,
        title = title,
        detail = detail,
        actionLabel = actionLabel,
        onAction = onAction,
        modifier = modifier,
        primary = true,
    )
}

@Composable
internal fun CoolapkErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "加载失败",
) {
    StateScaffold(
        icon = Icons.Rounded.CloudOff,
        title = title,
        detail = message,
        actionLabel = "重试",
        onAction = onRetry,
        modifier = modifier,
        primary = false,
    )
}

@Composable
private fun StateScaffold(
    icon: ImageVector,
    title: String,
    detail: String?,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    modifier: Modifier,
    primary: Boolean,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = RoundedCornerShape(26.dp),
            color = if (primary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            contentColor = if (primary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(34.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        detail?.takeIf(String::isNotBlank)?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/* ------------------------------ 头像与图标 ------------------------------ */

@Composable
internal fun UserAvatar(url: String?, name: String, size: Dp) {
    var failed by remember(url) { mutableStateOf(false) }
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(name.take(1), style = MaterialTheme.typography.titleLarge)
            if (!url.isNullOrBlank() && !failed) {
                AsyncImage(
                    model = coolapkImageRequest(url),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    onError = { failed = true },
                )
            }
        }
    }
}

@Composable
internal fun AppIcon(app: AppItem) {
    var failed by remember(app.iconUrl) { mutableStateOf(false) }
    Surface(
        modifier = Modifier.size(58.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(app.name.take(1), style = MaterialTheme.typography.titleLarge)
            app.iconUrl?.takeIf { !failed }?.let { url ->
                AsyncImage(
                    model = coolapkImageRequest(url),
                    contentDescription = "${app.name} 图标",
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop,
                    onError = { failed = true },
                )
            }
        }
    }
}

/* ------------------------------ 动态卡片 ------------------------------ */

@Composable
internal fun FeedCard(
    item: FeedItem,
    liked: Boolean,
    onLike: () -> Unit,
    onOpenDetail: () -> Unit,
    onReply: () -> Unit,
    onOpenAuthor: (String) -> Unit = {},
    onOpenImage: (List<String>, Int) -> Unit,
    card: Boolean = false,
    dividerAbove: Boolean = false,
    horizontalPadding: Dp = 16.dp,
) {
    val content: @Composable () -> Unit = {
        FeedHeader(item, onOpenAuthor)
        val message = item.message.toPlainText()
        val title = feedDisplayTitle(item.title, item.username)
        val expandable = message.length > 120
        var expanded by remember(item.id) { mutableStateOf(false) }
        title?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        if (message.isNotBlank()) {
            Spacer(Modifier.height(if (title == null) 10.dp else 6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                maxLines = if (expandable && !expanded) 4 else Int.MAX_VALUE,
                overflow = if (expandable && !expanded) TextOverflow.Ellipsis else TextOverflow.Clip,
            )
            if (expandable) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.heightIn(min = 44.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = if (expanded) "收起" else "展开全文",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        if (item.imageUrls.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FeedImages(item.imageUrls, onOpenImage)
        }
        Spacer(Modifier.height(12.dp))
        FeedActions(item = item, liked = liked, onLike = onLike, onReply = onReply)
    }

    if (card) {
        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenDetail),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ),
        ) {
            Column(Modifier.padding(14.dp)) { content() }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenDetail),
        ) {
            if (dividerAbove) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Column(
                Modifier.padding(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = 15.dp,
                    bottom = 8.dp,
                ),
            ) {
                content()
            }
        }
    }
}

internal fun feedDisplayTitle(title: String?, username: String): String? =
    title?.toPlainText()?.trim()?.takeIf { it.isNotEmpty() && it != "${username.trim()}的动态" }

@Composable
private fun FeedHeader(item: FeedItem, onOpenAuthor: (String) -> Unit) {
    Row(
        modifier = item.uid?.let { uid ->
            Modifier.clickable(role = Role.Button, onClickLabel = "查看${item.username}的主页") {
                onOpenAuthor(uid)
            }
        } ?: Modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(item.avatarUrl, item.username, 46.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.username,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            val subtitle = listOfNotNull(item.device, item.createdAt?.toDisplayTime()).joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun FeedImages(urls: List<String>, onOpenImage: (List<String>, Int) -> Unit) {
    if (urls.isEmpty()) return
    if (urls.size == 1) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 11f)
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClickLabel = "查看图片", onClick = { onOpenImage(urls, 0) }),
        ) {
            AsyncImage(
                model = coolapkImageRequest(urls.first()),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            ImageCountBadge("1/1", Modifier.align(Alignment.BottomEnd).padding(8.dp))
        }
        return
    }
    val columns = if (urls.size == 2 || urls.size == 4) 2 else 3
    val shown = if (urls.size > 4) urls.take(9) else urls
    val extra = urls.size - 9
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        shown.chunked(columns).forEachIndexed { rowIndex, rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowItems.forEachIndexed { columnIndex, url ->
                    val index = rowIndex * columns + columnIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClickLabel = "查看第 ${index + 1} 张图片", onClick = {
                                onOpenImage(urls, index)
                            }),
                    ) {
                        AsyncImage(
                            model = coolapkImageRequest(url),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        if (index == 8 && extra > 0) {
                            Box(
                                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.52f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "+$extra",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
                repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ImageCountBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.55f),
        contentColor = Color.White,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun FeedActions(
    item: FeedItem,
    liked: Boolean,
    onLike: () -> Unit,
    onReply: () -> Unit,
) {
    val context = LocalContext.current
    // 底部操作条：一条浅色胶囊轨道，三等分操作，点赞态用错误容器色高亮
    Surface(
        modifier = Modifier.fillMaxWidth().height(44.dp),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            FeedAction(
                icon = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                count = (item.likeCount + (if (liked) 1 else 0) - (if (item.isLiked) 1 else 0))
                    .coerceAtLeast(0).formatCompact(),
                description = "点赞",
                actionLabel = if (liked) "取消点赞" else "点赞",
                state = if (liked) "已点赞" else "未点赞",
                highlighted = liked,
                modifier = Modifier.weight(1f),
                onClick = onLike,
            )
            FeedAction(
                icon = Icons.Rounded.ChatBubbleOutline,
                count = item.replyCount.formatCompact(),
                description = "评论",
                modifier = Modifier.weight(1f),
                onClick = onReply,
            )
            FeedAction(
                icon = Icons.Rounded.Share,
                count = null,
                description = "分享",
                label = "分享",
                modifier = Modifier.weight(1f),
            ) {
                shareText(context, item)
            }
        }
    }
}

@Composable
private fun FeedAction(
    icon: ImageVector,
    count: String?,
    description: String,
    actionLabel: String = description,
    label: String? = null,
    state: String? = null,
    highlighted: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val trailing = label ?: count
    TextButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics {
                contentDescription = when {
                    label != null -> description
                    trailing.isNullOrBlank() -> description
                    else -> "$description，$trailing"
                }
                state?.let { stateDescription = it }
            },
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (highlighted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        if (!trailing.isNullOrBlank()) {
            Spacer(Modifier.width(6.dp))
            Text(trailing, modifier = Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.labelLarge)
        }
    }
}
