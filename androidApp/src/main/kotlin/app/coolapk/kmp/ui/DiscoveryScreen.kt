package app.coolapk.kmp.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import com.coolapk.kmp.core.DiscoveryTab
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.flow.filter
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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

    if (state.tabs.isEmpty()) {
        if (state.configLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ExpressiveLoadingIndicator() }
        } else {
            fallbackContent()
        }
        return
    }

    val selectedIndex = state.tabs.indexOfFirst { it.key == state.selectedTab?.key }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = selectedIndex) { state.tabs.size }
    // 频道栏点击或服务端选中 → 翻页；左右滑动停稳 → 选中对应频道
    LaunchedEffect(selectedIndex) {
        if (pagerState.currentPage != selectedIndex) pagerState.animateScrollToPage(selectedIndex)
    }
    // 只在一次滚动（手势或翻页动画）结束时判断：停在非当前频道上才视为用户滑动
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }.distinctUntilChanged().filter { !it }.collect {
            val page = pagerState.settledPage
            val tab = state.tabs.getOrNull(page) ?: return@collect
            if (tab.key == state.selectedTab?.key) return@collect
            val browserUrl = tab.browserUrl()
            if (browserUrl != null) {
                openDiscoveryBrowser(context, browserUrl)
                pagerState.animateScrollToPage(
                    state.tabs.indexOfFirst { it.key == state.selectedTab?.key }.coerceAtLeast(0),
                )
            } else {
                viewModel.selectTab(tab)
            }
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        SecondaryScrollableTabRow(
            selectedTabIndex = selectedIndex,
            modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth(),
            edgePadding = 16.dp,
        ) {
            state.tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = index == selectedIndex,
                    onClick = {
                        val browserUrl = tab.browserUrl()
                        if (browserUrl != null) openDiscoveryBrowser(context, browserUrl)
                        else viewModel.selectTab(tab)
                    },
                    text = { Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { page -> state.tabs.getOrNull(page)?.key ?: page },
        ) { page ->
            val tab = state.tabs[page]
            DiscoveryTabPage(
                tab = tab,
                section = state.sections[tab.key] ?: DiscoverySectionState(),
                isSelected = page == selectedIndex,
                viewModel = viewModel,
                feedState = feedState,
                onLike = onLike,
                onOpenFeed = onOpenFeed,
                onOpenImage = onOpenImage,
                openCard = openCard,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoveryTabPage(
    tab: DiscoveryTab,
    section: DiscoverySectionState,
    isSelected: Boolean,
    viewModel: DiscoveryViewModel,
    feedState: HomeUiState,
    onLike: (FeedItem) -> Unit,
    onOpenFeed: (String, FeedItem?, Boolean) -> Unit,
    onOpenImage: (List<String>, Int) -> Unit,
    openCard: (DiscoveryCard) -> Unit,
) {
    val context = LocalContext.current
    val browserUrl = tab.browserUrl()
    if (browserUrl != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CoolapkEmptyState(
                title = "该栏目在浏览器中打开",
                actionLabel = "打开",
                onAction = { openDiscoveryBrowser(context, browserUrl) },
            )
        }
        return
    }
    val listState = rememberLazyListState()
    val cards = section.cards
    val isSecondHandTab = tab.title.trim() == "二手"
    val groups = remember(cards, isSecondHandTab) {
        (if (isSecondHandTab) cards.filter { it.secondHand == null } else cards).displayGroups()
    }
    val secondHandRows = remember(cards, isSecondHandTab) {
        if (isSecondHandTab) cards.filter { it.secondHand != null }.chunked(2) else emptyList()
    }

    DisposableEffect(tab.key, listState) {
        onDispose {
            viewModel.saveScrollPosition(tab.key, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }
    }
    LaunchedEffect(tab.key, section.page > 0) {
        val (index, offset) = viewModel.scrollPosition(tab.key)
        if (section.page > 0 && groups.isNotEmpty()) {
            listState.scrollToItem(index.coerceAtMost(groups.lastIndex), offset)
        } else {
            listState.scrollToItem(0)
        }
    }
    LaunchedEffect(listState, isSelected) {
        if (!isSelected) return@LaunchedEffect
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd) viewModel.loadMore()
        }
    }

    val awaitingFirstPage = !isSelected && section.page == 0 && section.error == null
    PullToRefreshBox(
        isRefreshing = isSelected && section.loading && cards.isNotEmpty() && section.page <= 1,
        onRefresh = { if (isSelected) viewModel.refresh() },
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            (section.loading || awaitingFirstPage) && cards.isEmpty() ->
                ExpressiveLoadingIndicator(Modifier.align(Alignment.Center))
            section.error != null && cards.isEmpty() -> CoolapkErrorState(
                message = section.error ?: "加载失败",
                onRetry = { if (isSelected) viewModel.retry() },
                modifier = Modifier.align(Alignment.Center),
            )
            cards.isEmpty() -> CoolapkEmptyState(
                title = "暂无内容",
                modifier = Modifier.align(Alignment.Center),
                actionLabel = "重试",
                onAction = { if (isSelected) viewModel.retry() },
            )
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(groups, key = { it.first().id }) { group ->
                    if (group.all { it.entityType.equals("selectorLink", true) }) {
                        DiscoveryFilterRow(group, section.selectedFilterUrl) { card ->
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
                items(secondHandRows, key = { row -> "secondhand-${row.first().id}" }) { row ->
                    DiscoverySecondHandRow(row) { card ->
                        card.feed?.let { feed -> onOpenFeed(feed.id, feed, false) } ?: openCard(card)
                    }
                }
                if (section.loading) {
                    item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                            ExpressiveLoadingIndicator()
                        }
                    }
                }
                if (!section.loading && section.error == null && !section.hasMore) {
                    item(key = "end") {
                        Text(
                            "没有更多内容了",
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                section.error?.let { message ->
                    item(key = "error") {
                        TextButton(
                            onClick = { if (isSelected) viewModel.retry() },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text("$message · 重试")
                        }
                    }
                }
            }
        }
    }
}

/** 二手网格：两列等宽，单行不足两张时留出空位，保持卡片宽度一致。 */
@Composable
private fun DiscoverySecondHandRow(row: List<DiscoveryCard>, onOpen: (DiscoveryCard) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        row.forEach { card ->
            DiscoverySecondHandCard(
                card = card,
                modifier = Modifier.weight(1f),
                onClick = { onOpen(card) },
            )
        }
        if (row.size < 2) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun DiscoverySecondHandCard(card: DiscoveryCard, modifier: Modifier, onClick: () -> Unit) {
    val info = card.secondHand
    val feed = card.feed
    val cover = card.imageUrl ?: feed?.imageUrls?.firstOrNull()
    val title = (feed?.message ?: card.title ?: card.description).orEmpty().toPlainText()
    CoolapkCard(
        modifier = modifier,
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
    ) {
        if (cover != null) {
            AsyncImage(
                model = coolapkImageRequest(cover),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 5f),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier.fillMaxWidth().aspectRatio(4f / 5f).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(Modifier.padding(12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    info?.price.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                info?.platform?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatar(feed?.avatarUrl, feed?.username.orEmpty(), 28.dp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        feed?.username.orEmpty(),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta = listOfNotNull(feed?.createdAt, info?.location).joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Text(
                            meta,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private fun DiscoveryTab.browserUrl(): String? =
    webUrl?.takeIf { it.startsWith("http", ignoreCase = true) }
        ?: url.takeIf { it.startsWith("http", ignoreCase = true) }

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
        if (card.entityType.equals("divider", true)) continue
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
    val clickable = if (isDiscoveryCardActionable(target)) onClick else null
    when (card.entityType?.lowercase()) {
        "apk" -> DiscoveryAppCard(card, clickable)
        "user" -> DiscoveryUserCard(card, clickable)
        "topic", "feedtopic", "feedquestion" -> DiscoveryTopicCard(card, clickable)
        "product" -> DiscoveryProductCard(card, clickable)
        else -> DiscoveryImageCard(card, clickable)
    }
}

@Composable
private fun DiscoveryImageCard(card: DiscoveryCard, onClick: (() -> Unit)?) {
    CoolapkCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
    ) {
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

@Composable
private fun DiscoveryAppCard(card: DiscoveryCard, onClick: (() -> Unit)?) {
    CoolapkCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(card.imageUrl, card.title.orEmpty(), 64.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    card.title.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                card.description?.takeIf(String::isNotBlank)?.let {
                    Text(
                        it.toPlainText(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoveryUserCard(card: DiscoveryCard, onClick: (() -> Unit)?) {
    CoolapkCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(card.imageUrl, card.title.orEmpty(), 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    card.title.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                card.description?.takeIf(String::isNotBlank)?.let {
                    Text(
                        it.toPlainText(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoveryTopicCard(card: DiscoveryCard, onClick: (() -> Unit)?) {
    CoolapkCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.large,
        elevated = false,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                card.title.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            card.description?.takeIf(String::isNotBlank)?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it.toPlainText(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DiscoveryProductCard(card: DiscoveryCard, onClick: (() -> Unit)?) {
    CoolapkCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            card.imageUrl?.let { url ->
                AsyncImage(
                    model = coolapkImageRequest(url),
                    contentDescription = null,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    card.title.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                card.description?.takeIf(String::isNotBlank)?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        it.toPlainText(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
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
