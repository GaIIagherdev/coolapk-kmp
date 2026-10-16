package app.coolapk.kmp.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Comment
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coolapk.kmp.core.UserProfile
import app.coolapk.kmp.ui.theme.ThemeMode

private const val COOLAPK_HELP_URL = "https://m.coolapk.com/mp/do?c=help&m=questionNew"

private data class HubMenuItem(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

/**
 * 我的（移动端）：与参考项目的「我的」页结构一致——工具栏、身份行、动态/关注/粉丝、功能宫格、帮助入口；
 * 视觉采用 M3E 的分组表面与胶囊图标。没有对应页面的功能保留入口但禁用，并标注“暂未支持”。
 */
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
    onOpenEvents: () -> Unit,
    onOpenAppCenter: () -> Unit,
    onOpenGameCenter: () -> Unit,
    onOpenReviews: () -> Unit,
    onOpenSelector: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenMessages: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val darkNow = themeMode == ThemeMode.DARK || (themeMode == ThemeMode.SYSTEM && isSystemInDarkTheme())
    val items = listOf(
        HubMenuItem("我的动态", Icons.Rounded.Article, profile != null) { onOpenOwnTab(ProfileTab.FEEDS) },
        HubMenuItem("我的赞", Icons.Rounded.ThumbUp, profile != null) { onOpenOwnTab(ProfileTab.LIKED) },
        HubMenuItem("我的收藏", Icons.Rounded.Star, profile != null, onOpenFavorites),
        HubMenuItem("我的装备", Icons.Rounded.Inventory2, true, onOpenGoods),
        HubMenuItem("选机对比", Icons.Rounded.Compare, true, onOpenSelector),
        HubMenuItem("黑名单", Icons.Rounded.Block, profile != null, onOpenBlacklist),
        HubMenuItem("浏览历史", Icons.Rounded.History, profile != null, onOpenHistory),
        HubMenuItem("关注的论坛", Icons.Rounded.Forum, profile != null, onOpenFollowed),
        HubMenuItem("活动", Icons.Rounded.Event, true, onOpenEvents),
        HubMenuItem("应用中心", Icons.Rounded.Apps, true, onOpenAppCenter),
        HubMenuItem("游戏中心", Icons.Rounded.SportsEsports, true, onOpenGameCenter),
        HubMenuItem("评测区", Icons.Rounded.RateReview, true, onOpenReviews),
        HubMenuItem("私信", Icons.Rounded.Mail, true, onOpenMessages),
        HubMenuItem(if (darkNow) "日间模式" else "夜间模式", if (darkNow) Icons.Rounded.LightMode else Icons.Rounded.DarkMode, true) {
            onThemeModeChange(if (darkNow) ThemeMode.LIGHT else ThemeMode.DARK)
        },
        HubMenuItem("我的关注", Icons.Rounded.People, false) {},
        HubMenuItem("我的点评", Icons.Rounded.RateReview, false) {},
        HubMenuItem("我的评论", Icons.Rounded.Comment, profile != null) { onOpenOwnTab(ProfileTab.REPLY) },
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 900.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            // 工具栏：设置（消息铃铛已在顶栏）
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "设置")
                }
            }

            // 身份行：点击进入个人主页；未登录时点击登录
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .clickable { if (profile == null) onLogin() else onOpenProfile(profile.uid) }
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatar(profile?.avatarUrl, profile?.username.orEmpty(), 60.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        profile?.username ?: "登录 / 注册",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (profile != null) "UID ${profile.uid}" else "登录酷安，发现更多精彩",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(8.dp))

            // 动态 / 关注 / 粉丝
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                    HubStat("动态", profile?.feedCount, Modifier.weight(1f)) { profile?.let { onOpenProfile(it.uid) } }
                    HubStat("关注", profile?.followingCount, Modifier.weight(1f)) { profile?.let { onOpenProfile(it.uid) } }
                    HubStat("粉丝", profile?.followerCount, Modifier.weight(1f)) { profile?.let { onOpenProfile(it.uid) } }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 功能宫格：四列，与参考项目一致
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
            ) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    items.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { item ->
                                HubMenuCell(item, Modifier.weight(1f))
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 帮助入口
            ListItem(
                headlineContent = { Text("有疑惑请参考「酷安常见问题」", style = MaterialTheme.typography.bodyLarge) },
                leadingContent = { Icon(Icons.Rounded.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                modifier = Modifier.clip(MaterialTheme.shapes.large).clickable {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(COOLAPK_HELP_URL))) }
                },
            )

            if (profile != null) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text("退出登录", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HubStat(label: String, value: Int?, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value?.formatCompact() ?: "--",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HubMenuCell(item: HubMenuItem, modifier: Modifier) {
    val contentAlpha = if (item.enabled) 1f else 0.4f
    Column(
        modifier = modifier
            .heightIn(min = 88.dp)
            .padding(vertical = 10.dp)
            .semantics { if (!item.enabled) contentDescription = "${item.label}，暂未支持" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        FilledTonalIconButton(
            onClick = item.onClick,
            enabled = item.enabled,
            modifier = Modifier.size(52.dp),
        ) {
            Icon(item.icon, contentDescription = null)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
