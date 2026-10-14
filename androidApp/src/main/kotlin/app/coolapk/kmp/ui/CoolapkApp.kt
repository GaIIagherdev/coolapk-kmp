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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Settings
import app.coolapk.kmp.ui.theme.ThemeMode
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.ShortNavigationBarItem
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
import androidx.compose.runtime.key
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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

private sealed interface ContentRoute {
    data class Feed(val value: FeedRoute) : ContentRoute
    data class Topic(val tag: String) : ContentRoute
    data class User(val uid: String) : ContentRoute
    data object Settings : ContentRoute
    data object MyGoods : ContentRoute
    data object ProductSelector : ContentRoute
}

// material3 1.5.x 的 NavigationRail 不会自行约束宽度：放在 Row 里会吃掉整行，
// 必须由调用方给出固定宽度（M3 紧凑导航栏标准宽度 80dp）。
private val NavigationRailWidth = 80.dp

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
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
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
    var contentRoutes by remember { mutableStateOf<List<ContentRoute>>(emptyList()) }
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
    val openFeed: (FeedRoute) -> Unit = { route ->
        contentRoutes = contentRoutes + ContentRoute.Feed(route)
    }
    val openUser: (String) -> Unit = { uid ->
        uid.takeIf(String::isNotBlank)?.let { contentRoutes = contentRoutes + ContentRoute.User(it) }
    }
    val openTopic: (String) -> Unit = { tag ->
        tag.trim().trimStart('#').takeIf(String::isNotBlank)?.let {
            contentRoutes = contentRoutes + ContentRoute.Topic(it)
        }
    }
    val closeContent: () -> Unit = { contentRoutes = contentRoutes.dropLast(1) }
    val openSettings: () -> Unit = { contentRoutes = contentRoutes + ContentRoute.Settings }
    val openMyGoods: () -> Unit = { contentRoutes = contentRoutes + ContentRoute.MyGoods }
    val openProductSelector: () -> Unit = { contentRoutes = contentRoutes + ContentRoute.ProductSelector }

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
                    modifier = Modifier.width(NavigationRailWidth),
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
                        onCompare = if (selectedIndex == 1) openProductSelector else null,
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
                                    openFeed(FeedRoute(item.id, item, comments))
                                },
                                onOpenAuthor = openUser,
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
                                openFeed(FeedRoute(id, preview, comments))
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
                                openFeed(FeedRoute(id, preview, comments))
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
                                            openFeed(FeedRoute(item.id, item, comments))
                                        },
                                        onOpenAuthor = openUser,
                                        onOpenImage = openImages,
                                        onClearSearch = viewModel::clearSearch,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            },
                        )
                        4 -> MyHubScreen(
                            profile = state.profile,
                            notificationCount = state.notifications.total,
                            themeMode = themeMode,
                            onThemeModeChange = onThemeModeChange,
                            onLogin = { officialLoginOpen = true },
                            onOpenProfile = openUser,
                            onOpenGoods = openMyGoods,
                            onOpenSelector = openProductSelector,
                            onOpenSettings = openSettings,
                            onOpenNotifications = { notificationsOpen = true },
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
                            openFeed(FeedRoute(id))
                        }
                        com.coolapk.kmp.core.SearchResultKind.PRODUCT -> result.targetId?.let { id ->
                            openedProduct = id
                        }
                        com.coolapk.kmp.core.SearchResultKind.TOPIC -> result.targetId?.let(openTopic)
                        com.coolapk.kmp.core.SearchResultKind.USER -> result.targetId?.let(openUser)
                        else -> Unit
                    }
                },
            )
        }
        if (notificationsOpen) {
            NotificationScreen(
                onBack = { notificationsOpen = false },
                onOpenFeed = { id -> openFeed(FeedRoute(id)) },
                onLogin = {
                    notificationsOpen = false
                    officialLoginOpen = true
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
                onOpenFeed = { item -> openFeed(FeedRoute(item.id, item, false)) },
                onLike = viewModel::toggleLike,
                likeOverrides = state.likeOverrides,
            )
        }
        contentRoutes.forEachIndexed { index, route ->
            key(index, route) {
                when (route) {
                    is ContentRoute.Feed -> FeedDetailScreen(
                        feedId = route.value.id,
                        preview = route.value.preview,
                        focusComments = route.value.focusComments,
                        onOpenImage = openImages,
                        onOpenAuthor = openUser,
                        onBack = closeContent,
                        onFeedChanged = {
                            if (searchOpen) {
                                searchViewModel.retry()
                            } else if (selectedIndex == 3 && discoveryState.tabs.isNotEmpty()) {
                                discoveryViewModel.refresh()
                            } else {
                                viewModel.refresh(force = true)
                            }
                        },
                        viewModel = viewModel(
                            key = "feed:$index:${route.value.id}",
                            factory = FeedDetailViewModel.Factory,
                        ),
                    )
                    is ContentRoute.Topic -> TopicDetailScreen(
                        tag = route.tag,
                        onBack = closeContent,
                        onOpenFeed = { item, comments -> openFeed(FeedRoute(item.id, item, comments)) },
                        onOpenImage = openImages,
                        onLike = viewModel::toggleLike,
                        onOpenAuthor = openUser,
                        likedOverride = { id -> state.likeOverrides[id] },
                        viewModel = viewModel(
                            key = "topic:$index:${route.tag}",
                            factory = TopicDetailViewModel.Factory,
                        ),
                    )
                    is ContentRoute.ProductSelector -> ProductSelectorScreen(onBack = closeContent)
                    is ContentRoute.MyGoods -> state.profile?.uid?.let { ownUid ->
                        MyGoodsScreen(
                            uid = ownUid,
                            onBack = closeContent,
                            onOpenFeed = { item -> openFeed(FeedRoute(item.id, item, false)) },
                            onOpenProduct = { id ->
                                closeContent()
                                openedProduct = id
                            },
                            onLike = viewModel::toggleLike,
                            likeOverrides = state.likeOverrides,
                        )
                    }
                    is ContentRoute.Settings -> SettingsScreen(
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        onBack = closeContent,
                    )
                    is ContentRoute.User -> UserProfileScreen(
                        uid = route.uid,
                        onBack = closeContent,
                        onOpenFeed = { item, comments -> openFeed(FeedRoute(item.id, item, comments)) },
                        onOpenImage = openImages,
                        onLike = viewModel::toggleLike,
                        onOpenAuthor = openUser,
                        onLogin = { officialLoginOpen = true },
                        onImportCookie = { loginOpen = true },
                        onLogout = viewModel::logout,
                        likeOverrides = state.likeOverrides,
                        viewModel = viewModel(
                            key = "user:$index:${route.uid}",
                            factory = UserProfileViewModel.Factory,
                        ),
                    )
                }
            }
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
    onSettings: (() -> Unit)? = null,
    onGoods: (() -> Unit)? = null,
    onCompare: (() -> Unit)? = null,
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
                BadgedBox(
                    badge = {
                        if (notificationCount > 0) {
                            Badge(Modifier.clearAndSetSemantics {}) {
                                Text(if (notificationCount > 99) "99+" else notificationCount.toString())
                            }
                        }
                    },
                ) {
                    Icon(
                        Icons.Rounded.NotificationsNone,
                        contentDescription = if (notificationCount > 0) "消息，${notificationCount} 条未读" else "消息",
                    )
                }
            }
            IconButton(onClick = onRefresh, enabled = !refreshing) {
                Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
            }
            onCompare?.let {
                IconButton(onClick = it) {
                    Icon(Icons.Rounded.Compare, contentDescription = "选机对比")
                }
            }
            onGoods?.let {
                IconButton(onClick = it) {
                    Icon(Icons.Rounded.Inventory2, contentDescription = "我的装备")
                }
            }
            onSettings?.let {
                IconButton(onClick = it) {
                    Icon(Icons.Rounded.Settings, contentDescription = "设置")
                }
            }
        },
    )
}

@Composable
private fun AppNavigationBar(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    ShortNavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        arrangement = ShortNavigationBarArrangement.EqualWeight,
    ) {
        destinations.forEachIndexed { index, destination ->
            if (index == 2) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    PublishAction(onClick = { onSelect(index) })
                }
            } else {
                ShortNavigationBarItem(
                    selected = selectedIndex == index,
                    onClick = { onSelect(index) },
                    icon = { Icon(destination.icon, contentDescription = null) },
                    label = { Text(destination.label) },
                )
            }
        }
    }
}

@Composable
private fun PublishAction(onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(52.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Icon(Icons.Rounded.Add, contentDescription = "发布动态")
    }
}

@Composable
private fun AppNavigationRail(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationRail(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Spacer(Modifier.height(14.dp))
        destinations.forEachIndexed { index, destination ->
            if (index == 2) {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    PublishAction(onClick = { onSelect(index) })
                }
            } else {
                NavigationRailItem(
                    selected = selectedIndex == index,
                    onClick = { onSelect(index) },
                    icon = { Icon(destination.icon, contentDescription = null) },
                    label = { Text(destination.label) },
                )
            }
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
    onOpenAuthor: (String) -> Unit = {},
    onOpenImage: (List<String>, Int) -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    val pullState = rememberPullToRefreshState()

    LaunchedEffect(gridState, state.items.size, state.searchQuery) {
        snapshotFlow {
            val total = gridState.layoutInfo.totalItemsCount
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 3
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
                ExpressiveLoadingIndicator(Modifier.align(Alignment.Center))
            }
            state.items.isEmpty() && state.error != null -> {
                CoolapkErrorState(
                    message = state.error.orEmpty(),
                    onRetry = onRefresh,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            state.items.isEmpty() -> {
                CoolapkEmptyState(
                    title = if (state.searchQuery == null) "暂无动态" else "没有找到相关动态",
                    detail = if (state.searchQuery == null) "下拉刷新试试" else "试试其他关键词",
                    actionLabel = if (state.searchQuery == null) "刷新" else "返回首页",
                    onAction = if (state.searchQuery == null) onRefresh else onClearSearch,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            else -> BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                val columns = if (maxWidth >= 840.dp) 2 else 1
                val listHorizontalPadding = if (columns > 1) 12.dp else 16.dp
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    modifier = Modifier
                        .widthIn(max = 1100.dp)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        horizontal = if (columns > 1) 12.dp else 0.dp,
                        vertical = 8.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = if (columns > 1) Arrangement.spacedBy(8.dp) else Arrangement.Top,
                ) {
                    state.searchQuery?.let { query ->
                        item(key = "searchHeader", span = { GridItemSpan(maxLineSpan) }) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = listHorizontalPadding),
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
                    itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
                        FeedCard(
                            item = item,
                            liked = state.likeOverrides[item.id] ?: item.isLiked,
                            onLike = { onLike(item) },
                            onOpenDetail = { onOpenDetail(item, false) },
                            onReply = { onOpenDetail(item, true) },
                            onOpenAuthor = onOpenAuthor,
                            onOpenImage = onOpenImage,
                            card = columns > 1,
                            dividerAbove = columns == 1 && index > 0,
                        )
                    }
                    item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                        AnimatedVisibility(visible = state.loading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                ExpressiveLoadingIndicator(Modifier.size(28.dp))
                            }
                        }
                    }
                    state.error?.let { error ->
                        item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
                            Surface(
                                modifier = Modifier.padding(horizontal = listHorizontalPadding - 16.dp),
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
                    ExpressiveLoadingIndicator()
                }
                state.apps.isEmpty() && state.appsError != null -> CoolapkErrorState(
                    message = state.appsError,
                    onRetry = { onCategory(state.appCategory) },
                    modifier = Modifier.fillMaxSize(),
                )
                state.apps.isEmpty() -> CoolapkEmptyState(
                    title = "暂无应用",
                    detail = "可以切换分类或稍后刷新",
                    actionLabel = "刷新",
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
                                ExpressiveLoadingIndicator(Modifier.size(28.dp))
                            }
                        }
                    }
                    state.appsError?.let { error ->
                        item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
                            CoolapkErrorState(
                                message = error,
                                onRetry = if (state.appPage > 0) onLoadMore else ({ onCategory(state.appCategory) }),
                                modifier = Modifier.fillMaxWidth(),
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
