package app.coolapk.kmp.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.coolapk.kmp.core.NotificationCategory
import com.coolapk.kmp.core.NotificationItem
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    onBack: () -> Unit,
    onOpenFeed: (String) -> Unit,
    onLogin: () -> Unit,
    viewModel: NotificationViewModel = viewModel(factory = NotificationViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val categories = NotificationCategory.entries

    LaunchedEffect(viewModel) { viewModel.open() }
    LaunchedEffect(state.category) { listState.scrollToItem(0) }
    LaunchedEffect(listState, state.category, state.page, state.hasMore) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= layout.totalItemsCount - 3
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd && state.items.isNotEmpty()) viewModel.loadMore()
        }
    }
    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("消息", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.loading) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新通知")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SecondaryScrollableTabRow(
                selectedTabIndex = categories.indexOf(state.category).coerceAtLeast(0),
                edgePadding = 12.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) },
            ) {
                categories.forEach { category ->
                    Tab(
                        selected = state.category == category,
                        onClick = { viewModel.selectCategory(category) },
                        enabled = state.loggedIn != null,
                        text = { Text(category.title, maxLines = 1) },
                    )
                }
            }

            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.loggedIn == null && state.loading -> {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                    state.loggedIn == null -> {
                        NotificationStatus(
                            icon = { Icon(Icons.Rounded.WifiOff, contentDescription = null) },
                            title = "通知暂时无法加载",
                            detail = state.error,
                            action = "重试",
                            onAction = viewModel::retry,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    state.loggedIn == false -> {
                        NotificationStatus(
                            icon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                            title = "登录后查看通知",
                            action = "登录",
                            onAction = onLogin,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    state.items.isEmpty() && state.loading -> {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                    state.items.isEmpty() && state.error != null -> {
                        NotificationStatus(
                            icon = { Icon(Icons.Rounded.WifiOff, contentDescription = null) },
                            title = "通知暂时无法加载",
                            detail = state.error,
                            action = "重试",
                            onAction = viewModel::retry,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    state.items.isEmpty() -> {
                        NotificationStatus(
                            icon = { Icon(Icons.Rounded.NotificationsNone, contentDescription = null) },
                            title = "暂无${state.category.title}通知",
                            action = "刷新",
                            onAction = viewModel::refresh,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                            contentPadding = PaddingValues(bottom = 24.dp),
                        ) {
                            items(state.items, key = NotificationItem::id) { item ->
                                NotificationRow(item, onOpenFeed)
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 76.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                            if (state.loading) {
                                item(key = "loading") {
                                    Box(
                                        Modifier.fillMaxWidth().height(72.dp),
                                        contentAlignment = Alignment.Center,
                                    ) { CircularProgressIndicator(Modifier.size(26.dp)) }
                                }
                            }
                            state.error?.let { error ->
                                item(key = "error") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            error,
                                            modifier = Modifier.weight(1f),
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        TextButton(onClick = viewModel::retry) { Text("重试") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(item: NotificationItem, onOpenFeed: (String) -> Unit) {
    val feedId = item.feedId
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = feedId != null) { feedId?.let(onOpenFeed) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                item.actorName.firstOrNull()?.toString().orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            item.actorAvatarUrl?.let { url ->
                AsyncImage(
                    model = coolapkImageRequest(url),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize().clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.actorName,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.unreadCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
                Spacer(Modifier.weight(1f))
                item.createdAt?.let { time ->
                    Text(
                        time.toDisplayTime(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                item.message.toPlainText(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            item.targetPreview?.toPlainText()?.takeIf(String::isNotBlank)?.let { preview ->
                Text(
                    preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (feedId != null) {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp).align(Alignment.CenterVertically),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NotificationStatus(
    icon: @Composable () -> Unit,
    title: String,
    detail: String? = null,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        detail?.let {
            Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(onClick = onAction) { Text(action) }
    }
}
