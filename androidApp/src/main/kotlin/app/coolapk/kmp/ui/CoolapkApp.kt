package app.coolapk.kmp.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.coolapk.kmp.core.FeedItem
import com.coolapk.kmp.core.FeedChannel
import com.coolapk.kmp.core.AppItem
import kotlinx.coroutines.flow.distinctUntilChanged
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class Destination(
    val label: String,
    val icon: ImageVector,
)

private data class FeedRoute(
    val id: String,
    val preview: FeedItem? = null,
    val focusComments: Boolean = false,
)

private val destinations = listOf(
    Destination("首页", Icons.Rounded.Home),
    Destination("数码", Icons.Rounded.Apps),
    Destination("发布", Icons.Rounded.Add),
    Destination("发现", Icons.Rounded.Explore),
    Destination("我的", Icons.Rounded.Person),
)

@Composable
internal fun CoolapkApp(
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
    discoveryViewModel: DiscoveryViewModel = viewModel(factory = DiscoveryViewModel.Factory),
    digitalViewModel: DigitalViewModel = viewModel(factory = DigitalViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val discoveryState by discoveryViewModel.state.collectAsStateWithLifecycle()
    val digitalState by digitalViewModel.state.collectAsStateWithLifecycle()
    val appContext = LocalContext.current.applicationContext
    val publishViewModel: PublishViewModel = viewModel(
        factory = PublishViewModel.factory(appContext as android.app.Application),
    )
    val searchViewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory)
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    var publishOpen by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var notificationsOpen by rememberSaveable { mutableStateOf(false) }
    var openedFeed by remember { mutableStateOf<FeedRoute?>(null) }
    var openedApp by remember { mutableStateOf<AppItem?>(null) }
    var openedProduct by remember { mutableStateOf<String?>(null) }
    var openedImages by remember { mutableStateOf<ImageViewerSelection?>(null) }
    var officialLoginOpen by rememberSaveable { mutableStateOf(false) }
    var loginOpen by rememberSaveable { mutableStateOf(false) }
    val feedStateHolder = rememberSaveableStateHolder()
    val snackbarHostState = remember { SnackbarHostState() }
    val openImages: (List<String>, Int) -> Unit = { urls, index ->
        openedImages = ImageViewerSelection.create(urls, index)
    }

    BackHandler(openedApp != null) {
        openedApp = null
    }

    val refreshCurrentPage: () -> Unit = {
        when (selectedIndex) {
            0 -> viewModel.refresh(force = true)
            1 -> digitalViewModel.refresh()
            3 -> discoveryViewModel.refresh()
            4 -> viewModel.refreshProfile()
            else -> Unit
        }
    }

    LaunchedEffect(selectedIndex) {
        when (selectedIndex) {
            0 -> viewModel.selectChannel(FeedChannel.HOME)
            1 -> digitalViewModel.ensureLoaded()
            3 -> viewModel.selectChannel(FeedChannel.DISCOVERY)
            4 -> viewModel.loadProfile()
        }
        viewModel.loadNotifications()
    }

    LaunchedEffect(publishOpen) {
        if (publishOpen) publishViewModel.refreshSessionAndDraft()
    }

    LaunchedEffect(state.interactionError) {
        state.interactionError?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearInteractionError()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 840.dp
        Row(Modifier.fillMaxSize()) {
            if (expanded) {
                AppNavigationRail(
                    selectedIndex = selectedIndex,
                    onSelect = { index ->
                        if (index == 2) publishOpen = true else selectedIndex = index
                    },
                )
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.surface,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    AppTopBar(
                        title = when {
                            selectedIndex == 0 && state.searchQuery != null -> "搜索结果"
                            selectedIndex == 0 -> "酷安"
                            else -> destinations[selectedIndex].label
                        },
                        refreshing = when (selectedIndex) {
                            1 -> digitalState.configLoading || digitalState.content.loading || digitalState.libraryLoading
                            3 -> discoveryState.loading || discoveryState.configLoading
                            4 -> state.profileLoading
                            else -> state.refreshing
                        },
                        onRefresh = refreshCurrentPage,
                        onSearch = { searchOpen = true },
                        onNotifications = { notificationsOpen = true },
                        notificationCount = state.notifications.total,
                    )
                },
                bottomBar = {
                    if (!expanded) {
                        AppNavigationBar(
                            selectedIndex = selectedIndex,
                            onSelect = { index ->
                                if (index == 2) publishOpen = true else selectedIndex = index
                            },
                        )
                    }
                },
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    when (selectedIndex) {
                        0 -> feedStateHolder.SaveableStateProvider("home:${state.channel}:${state.searchQuery.orEmpty()}") {
                            HomeFeed(
                                state = state,
                                onRefresh = refreshCurrentPage,
                                onLoadMore = viewModel::loadMore,
                                onLike = viewModel::toggleLike,
                                onOpenDetail = { item, comments ->
                                    openedFeed = FeedRoute(item.id, item, comments)
                                },
                                onOpenImage = openImages,
                                onClearSearch = viewModel::clearSearch,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        1 -> DigitalScreen(
                            viewModel = digitalViewModel,
                            feedState = state,
                            onLike = viewModel::toggleLike,
                            onOpenFeed = { id, preview, comments ->
                                openedFeed = FeedRoute(id, preview, comments)
                            },
                            onOpenApp = { id, title ->
                                openedApp = AppItem(id, title, id, null, null, null, null, null)
                            },
                            onOpenProduct = { id -> openedProduct = id },
                            onOpenImage = openImages,
                        )
                        3 -> DiscoveryScreen(
                            viewModel = discoveryViewModel,
                            feedState = state,
                            onLike = viewModel::toggleLike,
                            onOpenFeed = { id, preview, comments ->
                                openedFeed = FeedRoute(id, preview, comments)
                            },
                            onOpenApp = { id, title ->
                                openedApp = AppItem(id, title, id, null, null, null, null, null)
                            },
                            onOpenImage = openImages,
                            fallbackContent = {
                                feedStateHolder.SaveableStateProvider("discovery:${state.channel}") {
                                    HomeFeed(
                                        state = state,
                                        onRefresh = { viewModel.refresh(force = true) },
                                        onLoadMore = viewModel::loadMore,
                                        onLike = viewModel::toggleLike,
                                        onOpenDetail = { item, comments ->
                                            openedFeed = FeedRoute(item.id, item, comments)
                                        },
                                        onOpenImage = openImages,
                                        onClearSearch = viewModel::clearSearch,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            },
                        )
                        4 -> ProfilePage(
                            state = state,
                            onRefresh = viewModel::refreshProfile,
                            onLogin = { officialLoginOpen = true },
                            onImportCookie = { loginOpen = true },
                            onLogout = viewModel::logout,
                        )
                    }
                }
            }
        }
        if (searchOpen) {
            SearchScreen(
                viewModel = searchViewModel,
                onBack = { searchOpen = false },
                onOpenResult = { result ->
                    when (result.kind) {
                        com.coolapk.kmp.core.SearchResultKind.APP -> result.targetId?.let { packageName ->
                            openedApp = AppItem(result.id, result.title, packageName,
                                result.imageUrl, result.subtitle, null, null, null)
                        }
                        com.coolapk.kmp.core.SearchResultKind.FEED -> result.targetId?.let { id ->
                            openedFeed = FeedRoute(id)
                        }
                        com.coolapk.kmp.core.SearchResultKind.PRODUCT -> result.targetId?.let { id ->
                            openedProduct = id
                        }
                        else -> Unit
                    }
                },
            )
        }
        if (notificationsOpen) {
            NotificationScreen(
                onBack = { notificationsOpen = false },
                onOpenFeed = { id -> openedFeed = FeedRoute(id) },
                onLogin = {
                    notificationsOpen = false
                    officialLoginOpen = true
                },
            )
        }
        openedFeed?.let { route ->
            FeedDetailScreen(
                feedId = route.id,
                preview = route.preview,
                focusComments = route.focusComments,
                onOpenImage = openImages,
                onBack = { openedFeed = null },
                onFeedChanged = {
                    if (searchOpen) {
                        searchViewModel.retry()
                    } else if (selectedIndex == 3 && discoveryState.tabs.isNotEmpty()) {
                        discoveryViewModel.refresh()
                    } else {
                        viewModel.refresh(force = true)
                    }
                },
            )
        }
        openedApp?.let { app ->
            AppDetailScreen(app = app, onBack = { openedApp = null }, onOpenImage = openImages)
        }
        openedProduct?.let { productId ->
            DigitalProductDetailScreen(
                productId = productId,
                viewModel = viewModel(factory = DigitalProductDetailViewModel.Factory),
                onBack = { openedProduct = null },
                onOpenImage = openImages,
            )
        }
        if (publishOpen) {
            PublishScreen(
                viewModel = publishViewModel,
                onBack = { publishOpen = false },
                onPublished = { _ ->
                    publishOpen = false
                    selectedIndex = 0
                    viewModel.refresh(force = true)
                },
            )
        }
        openedImages?.let { selection ->
            ImageViewerDialog(selection = selection, onDismiss = { openedImages = null })
        }
        if (officialLoginOpen) {
            OfficialLoginScreen(
                onDismiss = { officialLoginOpen = false },
                onManualImport = {
                    officialLoginOpen = false
                    loginOpen = true
                },
                onCredentials = { cookie, code, onResult ->
                    viewModel.loginWithAuthorization(cookie, code, onResult)
                },
            )
        }
    }

    if (loginOpen) {
        LoginDialog(
            onDismiss = { loginOpen = false },
            onSubmit = { cookie, onResult ->
                viewModel.loginWithCookie(cookie) { error -> onResult(error) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    title: String,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onSearch: () -> Unit,
    onNotifications: () -> Unit,
    notificationCount: Int,
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (title == "酷安") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        actions = {
            FilledTonalIconButton(onClick = onSearch) {
                Icon(Icons.Rounded.Search, contentDescription = "搜索")
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onNotifications) {
                Icon(
                    Icons.Rounded.NotificationsNone,
                    contentDescription = if (notificationCount > 0) "消息，${notificationCount} 条未读" else "消息",
                )
            }
            IconButton(onClick = onRefresh, enabled = !refreshing) {
                Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
            }
        },
    )
}

@Composable
private fun AppNavigationBar(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        destinations.forEachIndexed { index, destination ->
            NavigationBarItem(
                selected = selectedIndex == index,
                onClick = { onSelect(index) },
                icon = { Icon(destination.icon, contentDescription = destination.label) },
                label = { Text(destination.label) },
            )
        }
    }
}

@Composable
private fun AppNavigationRail(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Spacer(Modifier.height(14.dp))
        destinations.forEachIndexed { index, destination ->
            NavigationRailItem(
                selected = selectedIndex == index,
                onClick = { onSelect(index) },
                icon = { Icon(destination.icon, contentDescription = destination.label) },
                label = { Text(destination.label) },
            )
        }
    }
}

@Composable
private fun HomeFeed(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onLike: (FeedItem) -> Unit,
    onOpenDetail: (FeedItem, Boolean) -> Unit,
    onOpenImage: (List<String>, Int) -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState()

    LaunchedEffect(listState, state.items.size, state.searchQuery) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= state.items.lastIndex - 2
        }
            .distinctUntilChanged()
            .collect { nearEnd ->
                if (nearEnd && state.items.isNotEmpty()) onLoadMore()
            }
    }

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = modifier,
    ) {
        when {
            state.items.isEmpty() && state.loading -> {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            state.items.isEmpty() && state.error != null -> {
                ErrorState(
                    message = state.error.orEmpty(),
                    onRetry = onRefresh,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            state.items.isEmpty() -> {
                EmptyState(
                    title = if (state.searchQuery == null) "暂无动态" else "没有找到相关动态",
                    detail = if (state.searchQuery == null) "下拉刷新试试" else "试试其他关键词",
                    action = if (state.searchQuery == null) "刷新" else "返回首页",
                    onAction = if (state.searchQuery == null) onRefresh else onClearSearch,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .widthIn(max = 900.dp)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    state.searchQuery?.let { query ->
                        item(key = "searchHeader") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "“$query” 的搜索结果",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                IconButton(onClick = onClearSearch) {
                                    Icon(Icons.Rounded.Close, contentDescription = "返回首页")
                                }
                            }
                        }
                    }
                    items(state.items, key = FeedItem::id) { item ->
                        FeedCard(
                            item = item,
                            liked = state.likeOverrides[item.id] ?: item.isLiked,
                            onLike = { onLike(item) },
                            onOpenDetail = { onOpenDetail(item, false) },
                            onReply = { onOpenDetail(item, true) },
                            onOpenImage = onOpenImage,
                        )
                    }
                    item(key = "loading") {
                        AnimatedVisibility(visible = state.loading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(Modifier.size(28.dp))
                            }
                        }
                    }
                    state.error?.let { error ->
                        item(key = "error") {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = error,
                                        modifier = Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                    TextButton(onClick = if (state.page > 0) onLoadMore else onRefresh) {
                                        Text("重试")
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
internal fun FeedCard(
    item: FeedItem,
    liked: Boolean,
    onLike: () -> Unit,
    onOpenDetail: () -> Unit,
    onReply: () -> Unit,
    onOpenImage: (List<String>, Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenDetail),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            FeedHeader(item)
            val message = item.message.toPlainText()
            item.title?.takeIf(String::isNotBlank)?.let { title ->
                Spacer(Modifier.height(12.dp))
                Text(title.toPlainText(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            if (message.isNotBlank()) {
                Spacer(Modifier.height(if (item.title.isNullOrBlank()) 12.dp else 6.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 10,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.imageUrls.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FeedImages(item.imageUrls, onOpenImage)
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            Spacer(Modifier.height(8.dp))
            FeedActions(item = item, liked = liked, onLike = onLike, onReply = onReply)
        }
    }
}

@Composable
private fun FeedHeader(item: FeedItem) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        UserAvatar(item.avatarUrl, item.username, 46.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.username,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
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
internal fun UserAvatar(url: String?, name: String, size: androidx.compose.ui.unit.Dp) {
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
internal fun FeedImages(urls: List<String>, onOpenImage: (List<String>, Int) -> Unit) {
    if (urls.size == 1) {
        AsyncImage(
            model = coolapkImageRequest(urls.first()),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClickLabel = "查看图片", onClick = { onOpenImage(urls, 0) }),
            contentScale = ContentScale.Crop,
        )
    } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(urls.size) { index ->
                AsyncImage(
                    model = coolapkImageRequest(urls[index]),
                    contentDescription = null,
                    modifier = Modifier
                        .width(236.dp)
                        .height(176.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClickLabel = "查看第 ${index + 1} 张图片", onClick = {
                            onOpenImage(urls, index)
                        }),
                    contentScale = ContentScale.Crop,
                )
            }
        }
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeedAction(
            icon = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            count = (item.likeCount + (if (liked) 1 else 0) - (if (item.isLiked) 1 else 0))
                .coerceAtLeast(0).formatCompact(),
            description = "点赞",
            onClick = onLike,
        )
        FeedAction(Icons.Rounded.ChatBubbleOutline, item.replyCount.formatCompact(), "评论", onReply)
        FeedAction(Icons.Rounded.Share, null, "分享") { shareText(context, item) }
    }
}

@Composable
private fun FeedAction(
    icon: ImageVector,
    count: String?,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
        shape = RoundedCornerShape(50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(19.dp))
            if (!count.isNullOrBlank()) {
                Spacer(Modifier.width(6.dp))
                Text(count, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        Button(onClick = onRetry) { Text("重试") }
    }
}

@Composable
private fun EmptyState(
    title: String,
    detail: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.Explore,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun ReplyDialog(
    item: FeedItem,
    onDismiss: () -> Unit,
    onSubmit: (String, (String?) -> Unit) -> Unit,
) {
    var message by rememberSaveable(item.id) { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("回复 ${item.username}") },
        text = {
            Column {
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    label = { Text("评论内容") },
                    enabled = !submitting,
                    isError = error != null,
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text("取消") } },
        confirmButton = {
            Button(
                onClick = {
                    submitting = true
                    error = null
                    onSubmit(message.trim()) { result ->
                        if (result == null) onDismiss()
                        else {
                            error = result
                            submitting = false
                        }
                    }
                },
                enabled = message.isNotBlank() && !submitting,
            ) { Text(if (submitting) "发送中" else "发送") }
        },
    )
}

@Composable
private fun LoginDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, (String?) -> Unit) -> Unit,
) {
    var cookie by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("导入酷安 Cookie") },
        text = {
            Column {
                OutlinedTextField(
                    value = cookie,
                    onValueChange = { cookie = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    label = { Text("Cookie") },
                    supportingText = { Text("从酷安 WebView 会话复制 Cookie 后粘贴") },
                    enabled = !submitting,
                    isError = error != null,
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text("取消") } },
        confirmButton = {
            Button(
                onClick = {
                    submitting = true
                    error = null
                    onSubmit(cookie.trim()) { result ->
                        if (result == null) {
                            cookie = ""
                            onDismiss()
                        }
                        else {
                            error = result
                            submitting = false
                        }
                    }
                },
                enabled = cookie.isNotBlank() && !submitting,
            ) { Text(if (submitting) "登录中" else "登录") }
        },
    )
}

internal fun shareText(context: Context, item: FeedItem) {
    val summary = listOfNotNull(item.title?.toPlainText(), item.message.toPlainText())
        .filter(String::isNotBlank)
        .distinct()
        .joinToString("\n")
    val link = "https://www.coolapk.com/feed/${Uri.encode(item.id)}"
    val text = if (summary.isBlank()) "${item.username} 的动态\n$link"
    else "${item.username}：$summary\n$link"
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            },
            "分享动态",
        ),
    )
}

@Composable
private fun AppsPage(
    state: HomeUiState,
    onCategory: (String) -> Unit,
    onLoadMore: () -> Unit,
    onOpenDetail: (AppItem) -> Unit,
) {
    val categories = listOf("recommend" to "推荐", "newest" to "最新", "tools" to "工具", "social" to "社交")
    BoxWithConstraints(
        modifier = Modifier
            .widthIn(max = 980.dp)
            .fillMaxSize(),
    ) {
        val columns = if (maxWidth >= 840.dp) 2 else 1
        val gridState = rememberLazyGridState()

        LaunchedEffect(gridState, state.apps.size, state.appCategory, columns) {
            snapshotFlow {
                val lastVisible = gridState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: 0
                lastVisible >= state.apps.lastIndex - if (columns == 2) 4 else 2
            }
                .distinctUntilChanged()
                .collect { nearEnd -> if (nearEnd && state.apps.isNotEmpty()) onLoadMore() }
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            LazyRow(
                contentPadding = PaddingValues(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(categories) { (key, label) ->
                    val selected = key == state.appCategory
                    FilterChip(
                        selected = selected,
                        onClick = { if (!selected) onCategory(key) },
                        label = { Text(label) },
                    )
                }
            }
            when {
                state.appsLoading && state.apps.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.apps.isEmpty() && state.appsError != null -> ErrorState(
                    state.appsError,
                    { onCategory(state.appCategory) },
                    Modifier.fillMaxSize(),
                )
                state.apps.isEmpty() -> EmptyState(
                    title = "暂无应用",
                    detail = "可以切换分类或稍后刷新",
                    action = "刷新",
                    onAction = { onCategory(state.appCategory) },
                    modifier = Modifier.fillMaxSize(),
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.apps, key = AppItem::id) { app -> AppCard(app, onOpenDetail = { onOpenDetail(app) }) }
                    if (state.appsLoading) {
                        item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(28.dp))
                            }
                        }
                    }
                    state.appsError?.let { error ->
                        item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
                            ErrorState(
                                error,
                                if (state.appPage > 0) onLoadMore else ({ onCategory(state.appCategory) }),
                                Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppCard(app: AppItem, onOpenDetail: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenDetail),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(app.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                app.summary?.let {
                    Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val meta = listOfNotNull(
                    app.version,
                    app.score?.takeIf { it > 0 }?.let { "评分 $it" },
                    app.downloadCount,
                ).joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
private fun ProfilePage(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onLogin: () -> Unit,
    onImportCookie: () -> Unit,
    onLogout: () -> Unit,
) {
    val profile = state.profile
    if (state.profileLoading && profile == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (profile == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(state.profileError ?: "请先登录酷安账号", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onLogin) { Text("网页登录") }
            TextButton(onClick = onImportCookie) { Text("导入 Cookie") }
            TextButton(onClick = onRefresh) { Text("重新加载") }
        }
        return
    }
    Column(
        modifier = Modifier
            .widthIn(max = 900.dp)
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        UserAvatar(profile.avatarUrl, profile.username, 92.dp)
        Spacer(Modifier.height(14.dp))
        Text(profile.username, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        profile.bio?.takeIf(String::isNotBlank)?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            profile.feedCount?.let { ProfileStat("动态", it) }
            ProfileStat("关注", profile.followingCount)
            ProfileStat("粉丝", profile.followerCount)
        }
        Spacer(Modifier.height(28.dp))
        TextButton(onClick = onLogout) { Text("退出登录") }
    }
}

@Composable
private fun ProfileStat(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.formatCompact(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun String.toPlainText(): String = replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
    .replace(Regex("<[^>]+>"), "")
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .trim()

internal fun Int.formatCompact(): String = when {
    this >= 10_000 -> "${this / 10_000}.${(this % 10_000) / 1_000}万"
    this >= 1_000 -> "${this / 1_000}.${(this % 1_000) / 100}k"
    else -> toString()
}

internal fun String.toDisplayTime(): String {
    val timestamp = toLongOrNull() ?: return this
    val millis = if (timestamp < 100_000_000_000L) timestamp * 1_000 else timestamp
    val elapsed = System.currentTimeMillis() - millis
    return when {
        elapsed in 0L until 60_000L -> "刚刚"
        elapsed in 60_000L until 3_600_000L -> "${elapsed / 60_000}分钟前"
        elapsed in 3_600_000L until 86_400_000L -> "${elapsed / 3_600_000}小时前"
        elapsed in 86_400_000L until 604_800_000L -> "${elapsed / 86_400_000}天前"
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(millis))
    }
}

@Composable
internal fun coolapkImageRequest(url: String): ImageRequest {
    val context = LocalContext.current
    return remember(context, url) {
        ImageRequest.Builder(context).data(url).apply {
            if (Uri.parse(url).host.equals("image.coolapk.com", ignoreCase = true)) {
                httpHeaders(
                    NetworkHeaders.Builder()
                        .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .set("Referer", "https://www.coolapk.com/")
                        .build(),
                )
            }
        }.build()
    }
}
