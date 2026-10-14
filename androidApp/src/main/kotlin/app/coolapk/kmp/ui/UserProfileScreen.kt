package app.coolapk.kmp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coolapk.kmp.core.FeedItem
import com.coolapk.kmp.core.UserProfile
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

internal enum class ProfileTab(val label: String) {
    FEEDS("动态"),
    FAVORITES("收藏"),
    LIKED("赞过"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    uid: String?,
    onBack: () -> Unit,
    onOpenFeed: (FeedItem, Boolean) -> Unit,
    onOpenImage: (List<String>, Int) -> Unit,
    onLike: (FeedItem) -> Unit,
    onLogin: () -> Unit,
    onImportCookie: () -> Unit,
    onLogout: () -> Unit,
    onOpenAuthor: (String) -> Unit = {},
    ownProfile: UserProfile? = null,
    embedded: Boolean = false,
    likeOverrides: Map<String, Boolean> = emptyMap(),
    viewModel: UserProfileViewModel = viewModel(factory = UserProfileViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LaunchedEffect(uid, ownProfile) {
        listState.scrollToItem(0)
        viewModel.open(uid, ownProfile)
    }
    LaunchedEffect(listState, state.feeds.size, state.hasMoreFeeds, state.feedPage) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= listState.layoutInfo.totalItemsCount - 3
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd && state.feeds.isNotEmpty()) viewModel.loadMore()
        }
    }

    val tabState = remember(uid) { mutableStateOf(ProfileTab.FEEDS) }
    val activeTab = tabState.value
    val activeKind = when (activeTab) {
        ProfileTab.FEEDS -> null
        ProfileTab.FAVORITES -> ProfileListKind.FAVORITES
        ProfileTab.LIKED -> ProfileListKind.LIKED
    }
    val activeList = when (activeKind) {
        null -> null
        ProfileListKind.FAVORITES -> state.favorites
        ProfileListKind.LIKED -> state.liked
    }
    // 切换到收藏或赞过时加载第一页；刷新后列表页码归零，会在这里重新加载
    LaunchedEffect(activeKind, state.profile?.uid, activeList?.page) {
        activeKind?.let(viewModel::openList)
    }
    LaunchedEffect(listState, activeKind, activeList?.page, activeList?.hasMore) {
        if (activeKind == null) return@LaunchedEffect
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= listState.layoutInfo.totalItemsCount - 3
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd) viewModel.loadMoreList(activeKind)
        }
    }
    val content: @Composable (Modifier) -> Unit = { modifier ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = modifier,
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.widthIn(max = 900.dp).fillMaxSize().align(Alignment.TopCenter),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                when {
                    state.profile == null && state.profileLoading -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
                            ExpressiveLoadingIndicator()
                        }
                    }
                    state.profile == null -> item(key = "unavailable") {
                        ProfileUnavailable(
                            state = state,
                            ownPage = uid == null,
                            onRetry = viewModel::refresh,
                            onLogin = onLogin,
                            onImportCookie = onImportCookie,
                        )
                    }
                    else -> {
                        val profile = state.profile!!
                        item(key = "header") {
                            ProfileHeaderCard(
                                profile = profile,
                                isOwnPage = uid == null,
                                tab = tabState.value,
                                onTabChange = { tabState.value = it },
                                onLogout = onLogout,
                            )
                        }
                        if (state.profileError != null) item(key = "profile-error") {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    state.profileError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = viewModel::refresh) { Text("重试") }
                            }
                        }
                        when (activeTab) {
                            ProfileTab.FEEDS -> {
                                itemsIndexed(
                                    items = state.feeds,
                                    key = { _, item -> item.id },
                                ) { index, item ->
                                    FeedCard(
                                        item = item,
                                        liked = likeOverrides[item.id] ?: item.isLiked,
                                        onLike = { onLike(item) },
                                        onOpenDetail = { onOpenFeed(item, false) },
                                        onReply = { onOpenFeed(item, true) },
                                        onOpenImage = onOpenImage,
                                        onOpenAuthor = onOpenAuthor,
                                        dividerAbove = index > 0,
                                    )
                                }
                                if (state.feedsLoading && !state.refreshing) item(key = "loading-feeds") {
                                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                        ExpressiveLoadingIndicator()
                                    }
                                }
                                if (state.feedsError != null) item(key = "feeds-error") {
                                    CoolapkErrorState(
                                        message = state.feedsError.orEmpty(),
                                        onRetry = viewModel::retryFeeds,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                if (state.feedPage > 0 && state.feeds.isEmpty() && state.feedsError == null) {
                                    item(key = "feeds-empty") {
                                        CoolapkEmptyState(title = "暂无动态", modifier = Modifier.fillMaxWidth())
                                    }
                                }
                            }
                            ProfileTab.FAVORITES -> profileListItems(
                                keyPrefix = "favorites",
                                list = state.favorites,
                                emptyTitle = "还没有收藏的动态",
                                likeOverrides = likeOverrides,
                                onLike = onLike,
                                onOpenFeed = onOpenFeed,
                                onOpenImage = onOpenImage,
                                onOpenAuthor = onOpenAuthor,
                                onRetry = { viewModel.retryList(ProfileListKind.FAVORITES) },
                            )
                            ProfileTab.LIKED -> profileListItems(
                                keyPrefix = "liked",
                                list = state.liked,
                                emptyTitle = "还没有赞过的内容",
                                likeOverrides = likeOverrides,
                                onLike = onLike,
                                onOpenFeed = onOpenFeed,
                                onOpenImage = onOpenImage,
                                onOpenAuthor = onOpenAuthor,
                                onRetry = { viewModel.retryList(ProfileListKind.LIKED) },
                            )
                        }
                    }
                }
            }
        }
    }
    if (embedded) {
        content(Modifier.fillMaxSize())
    } else {
        BackHandler(onBack = onBack)
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            state.profile?.username ?: "个人主页",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::refresh, enabled = !state.profileLoading) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "刷新个人主页")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
        ) { padding -> content(Modifier.fillMaxSize().padding(padding)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileHeaderCard(
    profile: UserProfile,
    isOwnPage: Boolean,
    tab: ProfileTab,
    onTabChange: (ProfileTab) -> Unit,
    onLogout: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        // 封面为纯色块加几何装饰，本人用品牌主色，他人用次级色以作区分
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
                .clipToBounds()
                .background(
                    if (isOwnPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                ),
        ) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 60.dp, y = (-70).dp)
                    .size(220.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)),
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = 24.dp, y = 22.dp)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)),
            )
        }
        Box(Modifier.padding(horizontal = 16.dp).pullUp(54.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
            ) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 74.dp, bottom = 18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                profile.username,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "UID ${profile.uid}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (isOwnPage) {
                            OutlinedButton(
                                onClick = onLogout,
                                modifier = Modifier.padding(start = 8.dp),
                            ) {
                                Text("退出")
                            }
                        }
                    }
                    profile.bio?.toPlainText()?.takeIf(String::isNotBlank)?.let { bio ->
                        Spacer(Modifier.height(10.dp))
                        Text(bio, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                            ProfileStatCell("动态", profile.feedCount, Modifier.weight(1f))
                            ProfileStatCell("关注", profile.followingCount, Modifier.weight(1f))
                            ProfileStatCell("粉丝", profile.followerCount, Modifier.weight(1f))
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 16.dp, y = (-32).dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(4.dp),
            ) {
                UserAvatar(profile.avatarUrl, profile.username, 92.dp)
            }
        }
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            ProfileTab.entries.forEachIndexed { index, entry ->
                SegmentedButton(
                    selected = tab == entry,
                    onClick = { onTabChange(entry) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ProfileTab.entries.size),
                    label = { Text(entry.label) },
                )
            }
        }
    }
}

@Composable
private fun ProfileStatCell(label: String, value: Int?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value?.formatCompact() ?: "--",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun LazyListScope.profileListItems(
    keyPrefix: String,
    list: ProfileFeedList,
    emptyTitle: String,
    likeOverrides: Map<String, Boolean>,
    onLike: (FeedItem) -> Unit,
    onOpenFeed: (FeedItem, Boolean) -> Unit,
    onOpenImage: (List<String>, Int) -> Unit,
    onOpenAuthor: (String) -> Unit,
    onRetry: () -> Unit,
) {
    itemsIndexed(
        items = list.items,
        key = { _, item -> "$keyPrefix-${item.id}" },
    ) { index, item ->
        FeedCard(
            item = item,
            liked = likeOverrides[item.id] ?: item.isLiked,
            onLike = { onLike(item) },
            onOpenDetail = { onOpenFeed(item, false) },
            onReply = { onOpenFeed(item, true) },
            onOpenImage = onOpenImage,
            onOpenAuthor = onOpenAuthor,
            dividerAbove = index > 0,
        )
    }
    if (list.loading) item(key = "$keyPrefix-loading") {
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            ExpressiveLoadingIndicator()
        }
    }
    val error = list.error
    if (error != null) item(key = "$keyPrefix-error") {
        CoolapkErrorState(message = error, onRetry = onRetry, modifier = Modifier.fillMaxWidth())
    }
    if (list.page > 0 && list.items.isEmpty() && !list.loading && error == null) {
        item(key = "$keyPrefix-empty") {
            CoolapkEmptyState(title = emptyTitle, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun Modifier.pullUp(overlap: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val overlapPx = overlap.roundToPx()
    layout(placeable.width, (placeable.height - overlapPx).coerceAtLeast(0)) {
        placeable.place(0, -overlapPx)
    }
}

@Composable
private fun ProfileUnavailable(
    state: UserProfileUiState,
    ownPage: Boolean,
    onRetry: () -> Unit,
    onLogin: () -> Unit,
    onImportCookie: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 96.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = when {
                state.loginRequired -> "登录后查看个人主页"
                else -> state.profileError ?: "个人主页暂不可用"
            },
            style = MaterialTheme.typography.titleMedium,
        )
        if (ownPage && state.loginRequired) {
            Button(onClick = onLogin) { Text("网页登录") }
            TextButton(onClick = onImportCookie) { Text("导入 Cookie") }
        } else {
            TextButton(onClick = onRetry) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("重试")
            }
        }
    }
}
