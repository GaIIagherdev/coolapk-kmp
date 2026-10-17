package app.coolapk.kmp.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Comment
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coolapk.kmp.core.ListEntry
import com.coolapk.kmp.core.MyCard
import app.coolapk.kmp.ui.theme.ThemeMode
import com.coolapk.kmp.core.UserProfile

private const val COOLAPK_HELP_URL = "https://m.coolapk.com/mp/do?c=help&m=questionNew"

private data class HubTile(
    val label: String,
    val icon: ImageVector,
    val tint: TileTint,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

/** 宫格图标底色：与参考项目的彩色图标对应，取语义色角色。 */
private enum class TileTint { Primary, Secondary, Tertiary, Error, Neutral }

/**
 * 我的标签页：与参考项目的「我的」页结构一致——顶部工具图标、身份行、统计、功能宫格、帮助条、「我的卡片」。
 * 「我的常去」暂无对应接口，只保留入口（打开浏览历史）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MyHubScreen(
    profile: UserProfile?,
    notificationCount: Int,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onLogin: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenGoods: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenOwnTab: (ProfileTab) -> Unit,
    onOpenBlacklist: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenFollowed: () -> Unit,
    onOpenRelations: (String, RelationKind) -> Unit,
    onOpenTopic: (String) -> Unit,
    onOpenMore: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenMessages: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val darkNow = themeMode == ThemeMode.DARK || (themeMode == ThemeMode.SYSTEM && isSystemInDarkTheme())
    val signedIn = profile != null
    val tiles = listOf(
        HubTile("我的关注", Icons.Rounded.People, TileTint.Tertiary, signedIn) { profile?.let { onOpenRelations(it.uid, RelationKind.FOLLOW) } },
        HubTile("我的收藏", Icons.Rounded.Star, TileTint.Secondary, signedIn, onOpenFavorites),
        HubTile("我的点评", Icons.Rounded.RateReview, TileTint.Tertiary, signedIn) { onOpenOwnTab(ProfileTab.RATING) },
        HubTile(if (darkNow) "日间模式" else "夜间模式", if (darkNow) Icons.Rounded.LightMode else Icons.Rounded.DarkMode, TileTint.Secondary, true) {
            onThemeModeChange(if (darkNow) ThemeMode.LIGHT else ThemeMode.DARK)
        },
        HubTile("我的图文", Icons.Rounded.Article, TileTint.Error, signedIn) { onOpenOwnTab(ProfileTab.ARTICLE) },
        HubTile("我的回复", Icons.Rounded.Comment, TileTint.Error, signedIn) { onOpenOwnTab(ProfileTab.REPLY) },
        HubTile("我的装备", Icons.Rounded.Inventory2, TileTint.Primary, true, onOpenGoods),
        HubTile("更多", Icons.Rounded.MoreHoriz, TileTint.Neutral, true, onOpenMore),
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 900.dp).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
            // 沉浸式顶部：与状态栏融合，标题与图标自绘，不使用原生标题栏
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text("我的", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 4.dp))
                IconButton(onClick = onOpenMore) { Icon(Icons.Rounded.Apps, contentDescription = "更多内容") }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, contentDescription = "设置") }
                IconButton(onClick = onOpenMessages) {
                    BadgedBox(badge = {
                        if (notificationCount > 0) {
                            Badge { Text(if (notificationCount > 99) "99+" else notificationCount.toString()) }
                        }
                    }) {
                        Icon(Icons.Rounded.Mail, contentDescription = if (notificationCount > 0) "消息，${notificationCount} 条未读" else "消息")
                    }
                }
            }

            // 身份行：点击进入个人主页；未登录时点击登录
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .clickable { if (profile == null) onLogin() else onOpenProfile(profile.uid) }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatar(profile?.avatarUrl, profile?.username.orEmpty(), 68.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        profile?.username ?: "登录 / 注册",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (profile == null) "登录酷安，发现更多精彩" else "UID ${profile.uid}" + (profile.level?.let { " · Lv.$it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (profile != null) {
                    IconButton(onClick = { onOpenProfile(profile.uid) }) {
                        Icon(Icons.Rounded.QrCode2, contentDescription = "二维码名片")
                    }
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // 统计：动态 / 关注 / 粉丝，点击关注或粉丝进入关系列表
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(vertical = 14.dp),
            ) {
                HubStat("动态", profile?.feedCount, Modifier.weight(1f)) { profile?.let { onOpenProfile(it.uid) } }
                HubStatDivider()
                HubStat("关注", profile?.followingCount, Modifier.weight(1f)) { profile?.let { onOpenRelations(it.uid, RelationKind.FOLLOW) } }
                HubStatDivider()
                HubStat("粉丝", profile?.followerCount, Modifier.weight(1f)) { profile?.let { onOpenRelations(it.uid, RelationKind.FANS) } }
            }

            Spacer(Modifier.height(12.dp))

            // 功能宫格：4 列，彩色图标圆底
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(vertical = 8.dp),
            ) {
                tiles.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { tile -> HubTileCell(tile, Modifier.weight(1f)) }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            // 帮助条
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .clickable {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(COOLAPK_HELP_URL))) }
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("有疑惑请参考「酷安常见问题」", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // 我的卡片
            Row(
                Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text("我的卡片", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            FollowedTopicsCard(signedIn = signedIn, onOpenFollowed = onOpenFollowed, onOpenTopic = onOpenTopic)

            FrequentCardsSection(
                profile = profile,
                onOpenUrl = { url -> openCoolapkUrl(context, url) },
                onOpenLocal = { url -> openMyCardLocal(url, onOpenHistory, onOpenFavorites, onOpenFollowed) },
            )
            HistoryPreviewCard(signedIn = signedIn, onOpenHistory = onOpenHistory)

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 我关注的话题：横向的话题方块，点击进入话题；标题行进入关注列表。 */
@Composable
private fun FollowedTopicsCard(signedIn: Boolean, onOpenFollowed: () -> Unit, onOpenTopic: (String) -> Unit) {
    val viewModel: EntryListViewModel = viewModel(
        key = "my-hub-followed-topics",
        factory = EntryListViewModel.followedTopicsFactory(),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = signedIn, onClick = onOpenFollowed),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("我关注的话题", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        when {
            !signedIn -> Text("登录后查看关注的话题", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.items.isEmpty() && state.loading -> Text("加载中…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.items.isEmpty() -> Text("还没有关注的话题", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.items.take(8), key = { it.id }) { topic -> TopicTile(topic, onOpenTopic) }
            }
        }
    }
}

@Composable
private fun TopicTile(topic: ListEntry, onOpenTopic: (String) -> Unit) {
    Column(
        Modifier.width(84.dp).clickable { onOpenTopic(topic.title) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            val imageUrl = topic.imageUrl
            if (imageUrl != null) {
                AsyncImage(
                    model = coolapkImageRequest(imageUrl),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(topic.title.take(1), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(topic.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        topic.subtitle?.takeIf(String::isNotBlank)?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun HubTileCell(tile: HubTile, modifier: Modifier) {
    val (container, content) = when (tile.tint) {
        TileTint.Primary -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        TileTint.Secondary -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        TileTint.Tertiary -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        TileTint.Error -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        TileTint.Neutral -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier
            .clickable(enabled = tile.enabled, onClick = tile.onClick)
            .padding(vertical = 10.dp)
            .heightIn(min = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(tile.icon, contentDescription = null, tint = content)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            tile.label,
            style = MaterialTheme.typography.labelLarge,
            color = if (tile.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HubStat(label: String, value: Int?, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value?.let { formatHubCount(it) } ?: "--", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HubStatDivider() {
    Box(Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

private fun formatHubCount(value: Int): String = if (value >= 10_000) {
    val tenThousands = value / 1000 / 10.0
    "${"%.1f".format(tenThousands)}万"
} else {
    value.toString()
}

/** 我的常去：读取“我的卡片”配置中的常去分组；未登录时只显示提示，不发起请求。 */
@Composable
private fun FrequentCardsSection(
    profile: UserProfile?,
    onOpenUrl: (String) -> Unit,
    onOpenLocal: (String) -> Boolean,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text("我的常去", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "同步酷安个人中心“我的卡片”中的常用入口",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        if (profile == null) {
            Text("登录后查看我的常去", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FrequentCardsList(profile.uid, onOpenUrl, onOpenLocal)
        }
    }
}

@Composable
private fun FrequentCardsList(uid: String, onOpenUrl: (String) -> Unit, onOpenLocal: (String) -> Boolean) {
    val viewModel: MyCardsViewModel = viewModel(key = "my-hub-frequent-$uid", factory = MyCardsViewModel.factory())
    val state by viewModel.state.collectAsStateWithLifecycle()
    when {
        state.loading && state.cards.isEmpty() -> Text("加载中…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.error != null -> Row(
            Modifier.fillMaxWidth().clickable(onClick = viewModel::refresh).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${state.error}，点击重试", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
        }
        state.cards.isEmpty() -> Text(
            "暂无我的常去，可在酷安手机端的“我的卡片”中添加常用入口",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.cards, key = { it.id }) { card ->
                FrequentCardTile(card) {
                    val url = card.url ?: return@FrequentCardTile
                    if (!onOpenLocal(url)) onOpenUrl(url)
                }
            }
        }
    }
}

@Composable
private fun FrequentCardTile(card: MyCard, onClick: () -> Unit) {
    Column(
        Modifier
            .width(128.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = card.url != null, onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(96.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            val imageUrl = card.imageUrl
            if (imageUrl != null) {
                AsyncImage(
                    model = coolapkImageRequest(imageUrl),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(Icons.Rounded.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            card.title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

/** 浏览历史：下方的条目列表，最近几条，点击进入完整的浏览历史。 */
@Composable
private fun HistoryPreviewCard(signedIn: Boolean, onOpenHistory: () -> Unit) {
    val viewModel: EntryListViewModel = viewModel(
        key = "my-hub-history-preview",
        factory = EntryListViewModel.feedHistoryFactory(),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = signedIn, onClick = onOpenHistory),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("浏览历史", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            !signedIn -> Text("登录后查看浏览历史", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.items.isEmpty() && state.loading -> Text("加载中…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.items.isEmpty() -> Text("还没有浏览记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> state.items.take(3).forEach { entry -> HubEntryRow(entry, onClick = onOpenHistory) }
        }
    }
}

@Composable
private fun HubEntryRow(entry: ListEntry, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 无图时保留同样大小的占位，保证标题与简介始终对齐
        val imageUrl = entry.imageUrl
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = coolapkImageRequest(imageUrl),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(entry.title.take(1), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            entry.subtitle?.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** 「我的卡片」中的链接：能对应到本应用页面的直接打开，否则返回 false 交给浏览器。 */
private fun openMyCardLocal(url: String, onOpenHistory: () -> Unit, onOpenFavorites: () -> Unit, onOpenFollowed: () -> Unit): Boolean = when {
    url.contains("hitHistoryList") || url.contains("recentHistoryList") -> { onOpenHistory(); true }
    url.contains("myCollectionList") -> { onOpenFavorites(); true }
    url.contains("myFollowTopicList") -> { onOpenFollowed(); true }
    else -> false
}

private fun openCoolapkUrl(context: android.content.Context, url: String) {
    val target = if (url.startsWith("/")) "https://www.coolapk.com$url" else url
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))) }
}
