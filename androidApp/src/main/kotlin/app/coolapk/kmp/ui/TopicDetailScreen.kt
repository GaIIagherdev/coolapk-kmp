package app.coolapk.kmp.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.coolapk.kmp.core.FeedItem
import com.coolapk.kmp.core.TopicDetail
import com.coolapk.kmp.core.TopicFeedSort
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TopicDetailScreen(
    tag: String,
    onBack: () -> Unit,
    onOpenFeed: (FeedItem, Boolean) -> Unit,
    onOpenImage: (List<String>, Int) -> Unit,
    onLike: (FeedItem) -> Unit,
    likedOverride: (String) -> Boolean? = { null },
    onOpenAuthor: (String) -> Unit = {},
    viewModel: TopicDetailViewModel = viewModel(factory = TopicDetailViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(tag) { viewModel.open(tag) }
    LaunchedEffect(listState, state.page, state.hasMore) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= listState.layoutInfo.totalItemsCount - 3
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd && state.feeds.isNotEmpty()) viewModel.loadMore()
        }
    }
    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(state.detail?.title ?: tag.trim('#'), maxLines = 1,
                    overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新话题")
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.feedsLoading && state.page > 0 && state.feeds.isNotEmpty(),
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "overview") {
                    Column(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                        TopicOverview(state.detail, state.tag.ifBlank { tag }, state.detailLoading)
                        if (state.detailError != null) {
                            Text(state.detailError.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item(key = "sort") {
                    Column(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TopicFeedSort.entries.forEach { sort ->
                                FilterChip(
                                    selected = state.sort == sort,
                                    onClick = { viewModel.selectSort(sort) },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    label = { Text(sort.label) },
                                )
                            }
                        }
                    }
                }
                items(state.feeds, key = FeedItem::id) { feed ->
                    Box(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                        FeedCard(
                            item = feed,
                            liked = likedOverride(feed.id) ?: feed.isLiked,
                            onLike = { onLike(feed) },
                            onOpenDetail = { onOpenFeed(feed, false) },
                            onReply = { onOpenFeed(feed, true) },
                            onOpenImage = onOpenImage,
                            onOpenAuthor = onOpenAuthor,
                        )
                    }
                }
                if (state.feedsLoading) {
                    item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(28.dp))
                        }
                    }
                } else if (state.feedsError != null) {
                    item(key = "error") {
                        TextButton(
                            onClick = { if (state.page > 0) viewModel.loadMore() else viewModel.refresh() },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("${state.feedsError} · 重试") }
                    }
                } else if (state.feeds.isEmpty()) {
                    item(key = "empty") {
                        Text("暂无相关话题动态", modifier = Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (!state.hasMore) {
                    item(key = "end") {
                        Text("没有更多动态了", modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicOverview(detail: TopicDetail?, tag: String, loading: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(68.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(18.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("#", style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    detail?.logoUrl?.let { url ->
                        AsyncImage(
                            model = coolapkImageRequest(url),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(detail?.title ?: tag.trim('#'), style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                Text("话题", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
        val stats = listOfNotNull(
            detail?.viewCount?.let { "$it 热度" },
            detail?.discussionCount?.let { "$it 讨论" },
            detail?.followerCount?.let { "$it 关注" },
        )
        if (stats.isNotEmpty()) {
            Text(stats.joinToString("  ·  "), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        detail?.description?.takeIf(String::isNotBlank)?.let { description ->
            Text(description.toPlainText(), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (loading && detail == null) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
}
