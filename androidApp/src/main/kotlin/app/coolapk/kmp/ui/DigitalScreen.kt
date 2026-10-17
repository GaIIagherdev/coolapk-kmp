package app.coolapk.kmp.ui

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.coolapk.kmp.core.productSpecs
import com.coolapk.kmp.core.hasRequestArgs
import com.coolapk.kmp.core.listScore
import com.coolapk.kmp.core.listHeat
import kotlinx.coroutines.flow.filter
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor

@Composable
internal fun DigitalScreen(
    viewModel: DigitalViewModel,
    feedState: HomeUiState,
    onLike: (FeedItem) -> Unit,
    onOpenFeed: (String, FeedItem?, Boolean) -> Unit,
    onOpenApp: (String, String) -> Unit,
    onOpenProduct: (String) -> Unit,
    onOpenImage: (List<String>, Int) -> Unit,
    onSearch: () -> Unit,
    onCompare: () -> Unit,
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
        else -> DigitalChannels(
            state = state,
            onSearch = onSearch,
            onCompare = onCompare,
            viewModel = viewModel,
            onOpen = ::openEntity,
            onLike = onLike,
            onOpenFeed = onOpenFeed,
            onOpenImage = onOpenImage,
            feedState = feedState,
        )
    }
}

/** 频道的子栏目，筛选规则与参考项目一致。 */
private fun DigitalUiState.visibleSubtabs(tab: DigitalTab): List<DigitalEntity> {
    val channelTargets = tabs.flatMap { listOfNotNull(it.key, it.url, it.pageName, it.webUrl) }
        .map { it.removePrefix("#") }
        .toSet()
    // 与参考项目一致：地址不指向其它频道，或带有请求参数（同一页面的不同筛选）即为子栏目
    return tab.entities.filter { entity ->
        val target = entity.url.orEmpty().removePrefix("#")
        target.isNotBlank() && (target !in channelTargets || entity.hasRequestArgs)
    }
}

/** 数码频道：顶部可滚动频道栏 + 左右滑动翻页，每个频道独立保留列表与选中状态。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun DigitalChannels(
    state: DigitalUiState,
    viewModel: DigitalViewModel,
    onSearch: () -> Unit,
    onCompare: () -> Unit,
    onOpen: (DigitalEntity) -> Unit,
    onLike: (FeedItem) -> Unit,
    onOpenFeed: (String, FeedItem?, Boolean) -> Unit,
    onOpenImage: (List<String>, Int) -> Unit,
    feedState: HomeUiState,
) {
    val context = LocalContext.current
    val selectedIndex = state.tabs.indexOfFirst { it.key == state.selectedKey }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = selectedIndex) { state.tabs.size }
    LaunchedEffect(selectedIndex) {
        if (pagerState.currentPage != selectedIndex) pagerState.animateScrollToPage(selectedIndex)
    }
    // 只在一次滚动结束且停在非当前频道上时切换；网页频道改为打开浏览器并回弹
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }.distinctUntilChanged().filter { !it }.collect {
            val tab = state.tabs.getOrNull(pagerState.settledPage) ?: return@collect
            if (tab.key == state.selectedKey) return@collect
            val webUrl = tab.webUrl?.takeIf { it.startsWith("http") }
            if (webUrl != null) {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl))) }
                pagerState.animateScrollToPage(selectedIndex)
            } else {
                viewModel.selectTab(tab)
            }
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        DigitalHeader(onSearch = onSearch, onCompare = onCompare)
        // 频道：胶囊分段控件，选中项用浅色底，可横向滚动
        Surface(
            modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                state.tabs.forEachIndexed { index, tab ->
                    val selected = index == selectedIndex
                    Surface(
                        onClick = {
                            val webUrl = tab.webUrl?.takeIf { it.startsWith("http") }
                            if (webUrl != null) {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl))) }
                            } else {
                                viewModel.selectTab(tab)
                            }
                        },
                        shape = CircleShape,
                        color = if (selected) MaterialTheme.colorScheme.surfaceContainerLowest else Color.Transparent,
                        contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        Text(
                            tab.title,
                            modifier = Modifier.heightIn(min = 40.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { page -> state.tabs.getOrNull(page)?.key ?: page },
        ) { page ->
            val tab = state.tabs[page]
            val selected = page == selectedIndex
            val webUrl = tab.webUrl?.takeIf { it.startsWith("http") }
            when {
                webUrl != null -> DigitalWebTab(webUrl) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl))) }
                }
                tab.category -> if (selected) {
                    DigitalLibrary(
                        state = state,
                        onMode = viewModel::selectLibraryMode,
                        onSelect = viewModel::selectLibraryItem,
                        onRefresh = viewModel::refresh,
                        onLoadMore = viewModel::loadMore,
                        onOpen = onOpen,
                        onLike = onLike,
                        onOpenFeed = onOpenFeed,
                        onOpenImage = onOpenImage,
                        feedState = feedState,
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ExpressiveLoadingIndicator() }
                }
                else -> Column(Modifier.fillMaxSize()) {
                    val subtabs = if (selected) state.visibleSubtabs(tab) else emptyList()
                    if (subtabs.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(subtabs, key = DigitalEntity::id) { subtab ->
                                FilterChip(
                                    selected = state.subtabByTab[tab.key] == subtab.id,
                                    onClick = { viewModel.selectSubtab(tab, subtab) },
                                    label = { Text(subtab.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                )
                            }
                        }
                    }
                    DigitalEntityList(
                        content = state.contentByTab[tab.key]
                            ?: if (selected) DigitalContentState() else DigitalContentState(loading = true),
                        onRefresh = if (selected) viewModel::refresh else ({}),
                        onLoadMore = if (selected) viewModel::loadMore else ({}),
                        onOpen = onOpen,
                        onLike = onLike,
                        onOpenFeed = onOpenFeed,
                        onOpenImage = onOpenImage,
                        feedState = feedState,
                        modifier = Modifier.weight(1f),
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
    var query by rememberSaveable { mutableStateOf("") }
    var productGrid by rememberSaveable { mutableStateOf(true) }
    val options = remember(state.libraryOptions, query) {
        state.libraryOptions.filter { query.isBlank() || it.title.orEmpty().contains(query.trim(), ignoreCase = true) }
    }
    Column(Modifier.fillMaxSize()) {
        // 品牌 / 分类切换与搜索同一行：统一的浅色胶囊，无描边
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DigitalLibraryMode.entries.forEach { mode ->
                val selected = state.libraryMode == mode
                Surface(
                    onClick = { onMode(mode) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Text(
                        if (mode == DigitalLibraryMode.BRAND) "品牌" else "分类",
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            if (state.libraryMode == DigitalLibraryMode.BRAND) "搜索品牌" else "搜索分类",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val sideWidth = if (maxWidth >= 840.dp) 240.dp else 88.dp
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .width(sideWidth)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    when {
                        state.libraryLoading && state.libraryOptions.isEmpty() ->
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                ExpressiveLoadingIndicator()
                            }
                        state.libraryOptions.isEmpty() -> DigitalEmptyState(
                            state.libraryError ?: "暂无数据", onRefresh,
                        )
                        options.isEmpty() -> Text(
                            "没有匹配的结果",
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(options, key = DigitalEntity::id) { item ->
                                val selected = state.selectedLibraryItem?.id == item.id
                                LibrarySideItem(item, selected, compact = sideWidth < 240.dp) { onSelect(item) }
                            }
                        }
                    }
                }
                if (state.selectedLibraryItem == null && !state.libraryLoading) {
                    DigitalEmptyState("暂无品牌或分类", onRefresh, Modifier.weight(1f))
                } else {
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        // 当前选中项与视图切换
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    state.selectedLibraryItem?.title ?: "",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val count = state.libraryContent.items.count { it.isProductItem() }
                                if (count > 0) {
                                    Text(
                                        "共 $count 款产品",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Row(
                                Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                listOf(true to "网格", false to "列表").forEach { (grid, label) ->
                                    val selected = productGrid == grid
                                    Surface(
                                        onClick = { productGrid = grid },
                                        modifier = Modifier.heightIn(min = 40.dp),
                                        shape = CircleShape,
                                        color = if (selected) MaterialTheme.colorScheme.surfaceContainerLowest else Color.Transparent,
                                        contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    ) {
                                        Text(
                                            label,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    }
                                }
                            }
                        }
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
                                productGrid = productGrid,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibrarySideItem(item: DigitalEntity, selected: Boolean, compact: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(if (compact) 20.dp else 16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
    ) {
        if (compact) {
            Column(
                Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item.imageUrl?.let { url ->
                    AsyncImage(
                        model = coolapkImageRequest(url),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface),
                        contentScale = ContentScale.Fit,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    item.title ?: "未命名",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item.imageUrl?.let { url ->
                    AsyncImage(
                        model = coolapkImageRequest(url),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        contentScale = ContentScale.Fit,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    item.title ?: "未命名",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 列表中的一个显示块：系列标题、一组连续产品、或其它实体卡片。 */
private sealed interface DigitalBlock {
    val key: String

    data class SeriesTitle(val entity: DigitalEntity) : DigitalBlock {
        override val key = "title-${entity.id}"
    }

    data class Products(val items: List<DigitalEntity>) : DigitalBlock {
        override val key = "products-${items.first().id}"
    }

    data class Other(val entity: DigitalEntity) : DigitalBlock {
        override val key = "other-${entity.id}"
    }
}

private fun DigitalEntity.isProductItem(): Boolean =
    entityType.equals("product", true) && entities.isEmpty()

private fun List<DigitalEntity>.toDigitalBlocks(): List<DigitalBlock> {
    val blocks = mutableListOf<DigitalBlock>()
    var pending = mutableListOf<DigitalEntity>()
    fun flush() {
        if (pending.isNotEmpty()) {
            blocks += DigitalBlock.Products(pending.toList())
            pending = mutableListOf()
        }
    }
    for (entity in this) {
        when {
            entity.isProductItem() -> pending += entity
            entity.entityTemplate.equals("productGroupTitle", true) -> {
                flush()
                blocks += DigitalBlock.SeriesTitle(entity)
            }
            else -> {
                flush()
                blocks += DigitalBlock.Other(entity)
            }
        }
    }
    flush()
    return blocks
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
    productGrid: Boolean = true,
) {
    val listState = rememberLazyListState()
    val blocks = remember(content.items) { content.items.toDigitalBlocks() }
    LaunchedEffect(listState, content.items.size) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= listState.layoutInfo.totalItemsCount - 3
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
                items(blocks, key = DigitalBlock::key) { block ->
                    when (block) {
                        is DigitalBlock.SeriesTitle -> DigitalSeriesTitle(block.entity.title.orEmpty())
                        is DigitalBlock.Products -> if (productGrid) {
                            DigitalProductGrid(block.items, onOpen)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                block.items.forEach { DigitalProductCard(it, grid = false, onOpen = onOpen) }
                            }
                        }
                        is DigitalBlock.Other -> DigitalEntityCard(block.entity, onOpen, onLike, onOpenFeed, onOpenImage, feedState)
                    }
                }
                if (content.loading) item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        ExpressiveLoadingIndicator()
                    }
                }
                content.error?.let { error -> item(key = "error") {
                    TextButton(onClick = onLoadMore, modifier = Modifier.heightIn(min = 48.dp)) { Text("$error · 重试") }
                } }
                if (!content.loading && content.error == null && !content.hasMore && content.items.isNotEmpty()) {
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
            }
        }
    }
}

@Composable
private fun DigitalSeriesTitle(title: String) {
    if (title.isBlank()) return
    Text(
        title,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp, start = 4.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** 产品网格：两列等宽，奇数个时末尾留空位，卡片宽度保持一致。 */
@Composable
private fun DigitalProductGrid(items: List<DigitalEntity>, onOpen: (DigitalEntity) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { product ->
                    DigitalProductCard(product, grid = true, onOpen = onOpen, modifier = Modifier.weight(1f))
                }
                if (row.size < 2) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DigitalProductCard(
    product: DigitalEntity,
    grid: Boolean,
    onOpen: (DigitalEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val specs = product.productSpecs().take(3)
    CoolapkCard(
        modifier = modifier.fillMaxWidth(),
        onClick = { onOpen(product) },
        shape = MaterialTheme.shapes.large,
    ) {
        if (grid) {
            ProductImageBox(product, Modifier.fillMaxWidth().aspectRatio(4f / 3f))
            Column(Modifier.padding(12.dp)) {
                ProductTitle(product)
                ProductMetrics(product)
                ProductSpecChips(specs)
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ProductImageBox(product, Modifier.size(96.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    ProductTitle(product)
                    ProductMetrics(product)
                    ProductSpecChips(specs)
                }
            }
        }
    }
}

@Composable
private fun ProductImageBox(product: DigitalEntity, modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        product.imageUrl?.let { url ->
            AsyncImage(
                model = coolapkImageRequest(url),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(8.dp),
                contentScale = ContentScale.Fit,
            )
        } ?: Icon(Icons.Rounded.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProductTitle(product: DigitalEntity) {
    Text(
        product.title ?: "未命名产品",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 酷友评分与热度：只在列表数据里已有时显示，不为卡片额外请求详情。 */
@Composable
private fun ProductMetrics(product: DigitalEntity) {
    val score = product.listScore()
    val heat = product.listHeat()
    if (score == null && heat == null) return
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        score?.let {
            Text(
                "评分 ${"%.1f".format(it)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        heat?.let {
            Text(
                "热度 $it",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ProductSpecChips(specs: List<String>) {
    if (specs.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        specs.forEach { spec ->
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    spec,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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

/** 数码页自绘标题：标题、搜索和选机对比。 */
@Composable
private fun DigitalHeader(onSearch: () -> Unit, onCompare: () -> Unit) {
    Row(
        Modifier
            .widthIn(max = 900.dp)
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("数码", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, contentDescription = "搜索") }
        IconButton(onClick = onCompare) { Icon(Icons.Rounded.Compare, contentDescription = "选机对比") }
    }
}
