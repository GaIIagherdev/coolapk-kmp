package app.coolapk.kmp.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.coolapk.kmp.core.DigitalEntity
import com.coolapk.kmp.core.DigitalTab
import com.coolapk.kmp.core.FeedItem
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun DigitalScreen(
    viewModel: DigitalViewModel,
    feedState: HomeUiState,
    onLike: (FeedItem) -> Unit,
    onOpenFeed: (String, FeedItem?, Boolean) -> Unit,
    onOpenApp: (String, String) -> Unit,
    onOpenProduct: (String) -> Unit,
    onOpenImage: (List<String>, Int) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.ensureLoaded() }

    fun openEntity(entity: DigitalEntity) {
        val target = entity.url.orEmpty().removePrefix("#")
        val matchingTab = state.tabs.firstOrNull { tab ->
            target.isNotBlank() && (target == tab.url || target == tab.pageName || target == tab.key)
        }
        if (matchingTab != null) {
            viewModel.selectTab(matchingTab)
            return
        }
        val feed = entity.feed
        if (feed != null) {
            onOpenFeed(feed.id, feed, false)
            return
        }
        val feedId = FEED_TARGET.find(target)?.groupValues?.getOrNull(1)
        if (feedId != null) {
            onOpenFeed(feedId, null, false)
            return
        }
        val appId = APP_TARGET.find(target)?.groupValues?.getOrNull(1)
        if (appId != null) {
            onOpenApp(appId, entity.title ?: "应用详情")
            return
        }
        val productId = PRODUCT_TARGET.find(target)?.groupValues?.getOrNull(1)
            ?: entity.id.takeIf { entity.entityType?.contains("product", true) == true }
        if (!productId.isNullOrBlank()) {
            onOpenProduct(productId)
            return
        }
        val browser = discoveryBrowserUrl(target)
        if (browser != null) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(browser))) }
        } else if (entity.imageUrl != null) {
            onOpenImage(listOf(entity.imageUrl!!), 0)
        }
    }

    when {
        state.tabs.isEmpty() && state.configLoading ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ExpressiveLoadingIndicator() }
        state.tabs.isEmpty() -> DigitalEmptyState(
            state.configError ?: "暂无数码栏目",
            onRetry = viewModel::loadConfig,
        )
        else -> Column(Modifier.fillMaxSize()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.tabs, key = DigitalTab::key) { tab ->
                    FilterChip(
                        selected = state.selectedKey == tab.key,
                        onClick = { viewModel.selectTab(tab) },
                        label = { Text(tab.title) },
                    )
                }
            }
            val tab = state.selectedTab
            when {
                tab == null -> DigitalEmptyState("暂无数码栏目", viewModel::loadConfig)
                tab.webUrl?.let { url -> url.startsWith("http") } == true -> DigitalWebTab(tab.webUrl.orEmpty()) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tab.webUrl.orEmpty()))) }
                }
                tab.category -> DigitalLibrary(
                    state = state,
                    onMode = viewModel::selectLibraryMode,
                    onSelect = viewModel::selectLibraryItem,
                    onRefresh = viewModel::refresh,
                    onLoadMore = viewModel::loadMore,
                    onOpen = ::openEntity,
                    onLike = onLike,
                    onOpenFeed = onOpenFeed,
                    onOpenImage = onOpenImage,
                    feedState = feedState,
                )
                else -> key(tab.key) {
                    DigitalEntityList(
                        content = state.content,
                        onRefresh = viewModel::refresh,
                        onLoadMore = viewModel::loadMore,
                        onOpen = ::openEntity,
                        onLike = onLike,
                        onOpenFeed = onOpenFeed,
                        onOpenImage = onOpenImage,
                        feedState = feedState,
                    )
                }
            }
        }
    }
}

@Composable
private fun DigitalLibrary(
    state: DigitalUiState,
    onMode: (DigitalLibraryMode) -> Unit,
    onSelect: (DigitalEntity) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (DigitalEntity) -> Unit,
    onLike: (FeedItem) -> Unit,
    onOpenFeed: (String, FeedItem?, Boolean) -> Unit,
    onOpenImage: (List<String>, Int) -> Unit,
    feedState: HomeUiState,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DigitalLibraryMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.libraryMode == mode,
                    onClick = { onMode(mode) },
                    label = { Text(if (mode == DigitalLibraryMode.BRAND) "品牌" else "分类") },
                )
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val sideWidth = if (maxWidth >= 840.dp) 220.dp else 88.dp
            Row(Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier.width(sideWidth).fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    when {
                        state.libraryLoading && state.libraryOptions.isEmpty() ->
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                ExpressiveLoadingIndicator()
                            }
                        state.libraryOptions.isEmpty() -> DigitalEmptyState(
                            state.libraryError ?: "暂无数据", onRefresh,
                        )
                        else -> LazyColumn {
                            items(state.libraryOptions, key = DigitalEntity::id) { item ->
                                val selected = state.selectedLibraryItem?.id == item.id
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clickable { onSelect(item) },
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceContainerLow,
                                ) {
                                    Text(
                                        text = item.title ?: "未命名",
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
                if (state.selectedLibraryItem == null && !state.libraryLoading) {
                    DigitalEmptyState("暂无品牌或分类", onRefresh, Modifier.weight(1f))
                } else {
                    key(state.libraryMode, state.selectedLibraryItem?.id) {
                        DigitalEntityList(
                            content = state.libraryContent,
                            onRefresh = onRefresh,
                            onLoadMore = onLoadMore,
                            onOpen = onOpen,
                            onLike = onLike,
                            onOpenFeed = onOpenFeed,
                            onOpenImage = onOpenImage,
                            feedState = feedState,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DigitalEntityList(
    content: DigitalContentState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (DigitalEntity) -> Unit,
    onLike: (FeedItem) -> Unit,
    onOpenFeed: (String, FeedItem?, Boolean) -> Unit,
    onOpenImage: (List<String>, Int) -> Unit,
    feedState: HomeUiState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
) {
    val listState = rememberLazyListState()
    LaunchedEffect(listState, content.items.size) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= content.items.lastIndex - 2
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd && content.items.isNotEmpty()) onLoadMore()
        }
    }
    PullToRefreshBox(
        isRefreshing = content.loading && content.items.isNotEmpty() && content.page <= 1,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        when {
            content.loading && content.items.isEmpty() ->
                ExpressiveLoadingIndicator(Modifier.align(Alignment.Center))
            content.items.isEmpty() -> DigitalEmptyState(
                content.error ?: "暂无内容", onRefresh,
                Modifier.align(Alignment.Center),
            )
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth().align(Alignment.TopCenter),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(content.items, key = DigitalEntity::id) { entity ->
                    DigitalEntityCard(entity, onOpen, onLike, onOpenFeed, onOpenImage, feedState)
                }
                if (content.loading) item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        ExpressiveLoadingIndicator()
                    }
                }
                content.error?.let { error -> item(key = "error") {
                    TextButton(onClick = onLoadMore) { Text("$error · 重试") }
                } }
            }
        }
    }
}

@Composable
private fun DigitalEntityCard(
    entity: DigitalEntity,
    onOpen: (DigitalEntity) -> Unit,
    onLike: (FeedItem) -> Unit,
    onOpenFeed: (String, FeedItem?, Boolean) -> Unit,
    onOpenImage: (List<String>, Int) -> Unit,
    feedState: HomeUiState,
) {
    val template = entity.entityTemplate.orEmpty().lowercase()
    val children = entity.entities
    when {
        template.startsWith("imagecarouselcard") && children.isNotEmpty() ->
            DigitalCarousel(children, onOpen)
        template in setOf("iconlinkgridcard", "iconlongtitlegridcard", "icongridcard", "iconminigridcard") &&
            children.isNotEmpty() -> DigitalGrid(entity, onOpen)
        (template == "producttimelinelistcard" || template == "iconlistcard") && children.isNotEmpty() ->
            DigitalRows(entity, onOpen)
        children.isNotEmpty() && children.all { it.feed != null } -> Column {
            DigitalHeading(entity, onOpen)
            children.forEach { child ->
                child.feed?.let { feed ->
                    FeedCard(
                        item = feed,
                        liked = feedState.likeOverrides[feed.id] ?: feed.isLiked,
                        onLike = { onLike(feed) },
                        onOpenDetail = { onOpenFeed(feed.id, feed, false) },
                        onReply = { onOpenFeed(feed.id, feed, true) },
                        onOpenImage = onOpenImage,
                        card = true,
                    )
                }
            }
        }
        entity.feed != null -> entity.feed?.let { feed ->
            FeedCard(
                item = feed,
                liked = feedState.likeOverrides[feed.id] ?: feed.isLiked,
                onLike = { onLike(feed) },
                onOpenDetail = { onOpenFeed(feed.id, feed, false) },
                onReply = { onOpenFeed(feed.id, feed, true) },
                onOpenImage = onOpenImage,
                card = true,
            )
        }
        else -> Card(
            onClick = { onOpen(entity) },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                DigitalEntityImage(entity, Modifier.size(64.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(entity.title ?: "数码内容", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    entity.subTitle?.let {
                        Text(it.toPlainText(), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null)
            }
        }
    }
}

@Composable
private fun DigitalCarousel(children: List<DigitalEntity>, onOpen: (DigitalEntity) -> Unit) {
    val pager = rememberPagerState(pageCount = { children.size })
    Box(Modifier.fillMaxWidth()) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()) { index ->
            val entity = children[index]
            Box(Modifier.fillMaxWidth().aspectRatio(4.5f).clickable { onOpen(entity) }) {
                entity.imageUrl?.let { url ->
                    AsyncImage(
                        model = coolapkImageRequest(url),
                        contentDescription = entity.title ?: "数码推荐",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
        if (children.size > 1) {
            Text(
                "${pager.currentPage + 1}/${children.size}",
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun DigitalGrid(entity: DigitalEntity, onOpen: (DigitalEntity) -> Unit) {
    val navigation = entity.entityTemplate.equals("iconlinkgridcard", true)
    val columns = if (navigation) 5 else 4
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DigitalHeading(entity, onOpen)
            entity.entities.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { item ->
                        Column(
                            Modifier.weight(1f).clickable { onOpen(item) }.padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            DigitalEntityImage(item, Modifier.size(if (navigation) 38.dp else 50.dp))
                            Text(
                                item.title ?: "未命名",
                                modifier = Modifier.padding(top = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                minLines = if (navigation) 1 else 2,
                                maxLines = if (navigation) 1 else 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun DigitalRows(entity: DigitalEntity, onOpen: (DigitalEntity) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            DigitalHeading(entity, onOpen)
            entity.entities.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().height(72.dp).clickable { onOpen(item) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DigitalEntityImage(item, Modifier.size(42.dp))
                    Text(
                        item.title ?: "未命名",
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun DigitalHeading(entity: DigitalEntity, onOpen: (DigitalEntity) -> Unit) {
    val title = entity.title?.takeIf(String::isNotBlank) ?: return
    val hasAction = !entity.url.isNullOrBlank()
    SectionHeader(
        title = title,
        horizontalPadding = 0.dp,
        actionLabel = if (hasAction) (entity.subTitle ?: "更多") else null,
        onAction = if (hasAction) ({ onOpen(entity) }) else null,
    )
}

@Composable
private fun DigitalEntityImage(entity: DigitalEntity, modifier: Modifier = Modifier) {
    if (entity.imageUrl != null) {
        AsyncImage(
            model = coolapkImageRequest(entity.imageUrl!!),
            contentDescription = entity.title,
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Devices, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DigitalWebTab(url: String, onOpen: () -> Unit) {
    DigitalEmptyState("此栏目由网页提供", onOpen, buttonLabel = "打开页面")
}

@Composable
private fun DigitalEmptyState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    buttonLabel: String = "重试",
) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        TextButton(onClick = onRetry) { Text(buttonLabel) }
    }
}

private val FEED_TARGET = Regex("(?:^|[/#])feed/(\\d+)", RegexOption.IGNORE_CASE)
private val APP_TARGET = Regex("(?:^|[/#])apk/([A-Za-z0-9._-]+)", RegexOption.IGNORE_CASE)
private val PRODUCT_TARGET = Regex("(?:^|[/#])product/(\\d+)", RegexOption.IGNORE_CASE)
