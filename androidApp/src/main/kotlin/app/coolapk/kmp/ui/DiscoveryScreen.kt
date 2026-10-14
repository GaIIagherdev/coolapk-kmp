package app.coolapk.kmp.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.coolapk.kmp.core.DiscoveryCard
import com.coolapk.kmp.core.FeedItem
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiscoveryScreen(
    viewModel: DiscoveryViewModel,
    feedState: HomeUiState,
    onLike: (FeedItem) -> Unit,
    onOpenFeed: (String, FeedItem?, Boolean) -> Unit,
    onOpenApp: (String, String) -> Unit,
    onOpenImage: (List<String>, Int) -> Unit,
    fallbackContent: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val selectedKey = state.selectedTab?.key
    val groups = remember(state.cards) { state.cards.displayGroups() }
    val openCard: (DiscoveryCard) -> Unit = { card ->
        val target = card.url.orEmpty()
        val feedId = FEED_LINK.find(target)?.groupValues?.getOrNull(1)
        val appId = APP_LINK.find(target)?.groupValues?.getOrNull(1)
        when {
            feedId != null -> onOpenFeed(feedId, null, false)
            appId != null -> onOpenApp(appId, card.title ?: "应用详情")
            else -> discoveryBrowserUrl(target)?.let { openDiscoveryBrowser(context, it) }
        }
    }
    LaunchedEffect(Unit) { viewModel.ensureLoaded() }
    DisposableEffect(selectedKey, listState) {
        onDispose {
            selectedKey?.let { key ->
                viewModel.saveScrollPosition(key, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            }
        }
    }
    LaunchedEffect(selectedKey, state.page > 0) {
        if (selectedKey != null) {
            val (index, offset) = viewModel.scrollPosition(selectedKey)
            if (state.page > 0 && groups.isNotEmpty()) {
                listState.scrollToItem(index.coerceAtMost(groups.lastIndex), offset)
            } else {
                listState.scrollToItem(0)
            }
        }
    }
    LaunchedEffect(listState, groups.size, state.selectedTab?.key) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= groups.lastIndex - 2
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd && state.cards.isNotEmpty()) viewModel.loadMore()
        }
    }

    if (state.tabs.isEmpty()) {
        if (state.configLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ExpressiveLoadingIndicator() }
        } else {
            fallbackContent()
        }
        return
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        LazyRow(
            modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.tabs, key = { it.key }) { tab ->
                FilterChip(
                    selected = state.selectedTab?.key == tab.key,
                    onClick = {
                        val webUrl = tab.webUrl
                        if (webUrl != null && webUrl.startsWith("http")) {
                            openDiscoveryBrowser(context, webUrl)
                        } else {
                            selectedKey?.let { key ->
                                viewModel.saveScrollPosition(key, listState.firstVisibleItemIndex,
                                    listState.firstVisibleItemScrollOffset)
                            }
                            viewModel.selectTab(tab)
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                    label = { Text(tab.title) },
                )
            }
        }
        PullToRefreshBox(
            isRefreshing = state.loading && state.cards.isNotEmpty() && state.page <= 1,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.loading && state.cards.isEmpty() ->
                    ExpressiveLoadingIndicator(Modifier.align(Alignment.Center))
                state.error != null && state.cards.isEmpty() -> CoolapkErrorState(
                    message = state.error ?: "加载失败",
                    onRetry = viewModel::retry,
                    modifier = Modifier.align(Alignment.Center),
                )
                state.cards.isEmpty() -> CoolapkEmptyState(
                    title = "暂无内容",
                    modifier = Modifier.align(Alignment.Center),
                    actionLabel = "重试",
                    onAction = viewModel::retry,
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(groups, key = { it.first().id }) { group ->
                        if (group.all { it.entityType.equals("selectorLink", true) }) {
                            DiscoveryFilterRow(group, state.selectedFilterUrl) { card ->
                                if (card.url.orEmpty().startsWith("http", ignoreCase = true)) {
                                    openCard(card)
                                } else {
                                    viewModel.selectFilter(card)
                                }
                            }
                        } else {
                            val card = group.first()
                            card.feed?.let { feed ->
                                FeedCard(
                                    item = feed,
                                    liked = feedState.likeOverrides[feed.id] ?: feed.isLiked,
                                    onLike = { onLike(feed) },
                                    onOpenDetail = { onOpenFeed(feed.id, feed, false) },
                                    onReply = { onOpenFeed(feed.id, feed, true) },
                                    onOpenImage = onOpenImage,
                                    card = true,
                                )
                            } ?: DiscoveryItemCard(card) {
                                openCard(card)
                            }
                        }
                    }
                    if (state.loading) {
                        item(key = "loading") {
                            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                                ExpressiveLoadingIndicator()
                            }
                        }
                    }
                    state.error?.let { message ->
                        item(key = "error") {
                            TextButton(onClick = viewModel::retry, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text("$message · 重试")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveryFilterRow(
    cards: List<DiscoveryCard>,
    selectedUrl: String?,
    onSelect: (DiscoveryCard) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(cards, key = DiscoveryCard::id) { card ->
            val target = card.url.orEmpty()
            FilterChip(
                selected = selectedUrl == target,
                onClick = { onSelect(card) },
                enabled = isDiscoveryFilterUrl(target) || discoveryBrowserUrl(target) != null,
                modifier = Modifier.heightIn(min = 48.dp),
                label = { Text(card.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

private fun List<DiscoveryCard>.displayGroups(): List<List<DiscoveryCard>> {
    val groups = mutableListOf<MutableList<DiscoveryCard>>()
    for (card in this) {
        val links = card.entityType.equals("selectorLink", true)
        if (links && groups.lastOrNull()?.lastOrNull()?.entityType.equals("selectorLink", true)) {
            groups.last().add(card)
        } else {
            groups.add(mutableListOf(card))
        }
    }
    return groups
}

@Composable
private fun DiscoveryItemCard(card: DiscoveryCard, onClick: () -> Unit) {
    val target = card.url.orEmpty()
    val actionable = isDiscoveryCardActionable(target)
    Card(
        modifier = Modifier.fillMaxWidth().then(
            if (actionable) Modifier.clickable(onClick = onClick) else Modifier,
        ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.fillMaxWidth()) {
            card.imageUrl?.let { url ->
                AsyncImage(
                    model = coolapkImageRequest(url),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(190.dp),
                    contentScale = ContentScale.Crop,
                )
            }
            if (!card.title.isNullOrBlank() || !card.description.isNullOrBlank()) {
                Column(Modifier.padding(16.dp)) {
                    card.title?.takeIf(String::isNotBlank)?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    card.description?.takeIf(String::isNotBlank)?.let {
                        if (!card.title.isNullOrBlank()) Spacer(Modifier.height(6.dp))
                        Text(
                            it.toPlainText(),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun openDiscoveryBrowser(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

internal fun isDiscoveryCardActionable(target: String): Boolean =
    FEED_LINK.containsMatchIn(target) || APP_LINK.containsMatchIn(target) || discoveryBrowserUrl(target) != null

internal fun discoveryBrowserUrl(target: String): String? = when {
    target.startsWith("http://") || target.startsWith("https://") -> target
    target.startsWith("#/") -> "https://www.coolapk.com/$target"
    target.startsWith("/") && !target.startsWith("//") -> "https://www.coolapk.com$target"
    else -> null
}

private val FEED_LINK = Regex("(?:^|[/#])feed/(\\d+)", RegexOption.IGNORE_CASE)
private val APP_LINK = Regex("(?:^|[/#])apk/([A-Za-z0-9._-]+)", RegexOption.IGNORE_CASE)
