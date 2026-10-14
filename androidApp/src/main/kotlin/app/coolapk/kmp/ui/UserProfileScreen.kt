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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coolapk.kmp.core.FeedItem
import com.coolapk.kmp.core.UserProfile
import kotlinx.coroutines.flow.distinctUntilChanged

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

    val content: @Composable (Modifier) -> Unit = { modifier ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = modifier,
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.widthIn(max = 900.dp).fillMaxSize().align(Alignment.TopCenter),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    state.profile == null && state.profileLoading -> item(key = "loading") {
                        Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
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
                        item(key = "header") {
                            ProfileHeader(state.profile!!, isOwnPage = uid == null, onLogout = onLogout)
                        }
                        if (state.profileError != null) item(key = "profile-error") {
                            Row(
                                Modifier.fillMaxWidth(),
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
                        item(key = "section") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "动态",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                state.profile!!.feedCount?.let { count ->
                                    Text(
                                        text = count.formatCompact(),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        items(
                            count = state.feeds.size,
                            key = { index -> state.feeds[index].id },
                        ) { index ->
                            val item = state.feeds[index]
                            FeedCard(
                                item = item,
                                liked = likeOverrides[item.id] ?: item.isLiked,
                                onLike = { onLike(item) },
                                onOpenDetail = { onOpenFeed(item, false) },
                                onReply = { onOpenFeed(item, true) },
                                onOpenImage = onOpenImage,
                                onOpenAuthor = onOpenAuthor,
                            )
                        }
                        if (state.feedsLoading && !state.refreshing) item(key = "loading-feeds") {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        if (state.feedsError != null) item(key = "feeds-error") {
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(state.feedsError!!, color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = viewModel::retryFeeds) { Text("重试") }
                            }
                        }
                        if (state.feedPage > 0 && state.feeds.isEmpty() && state.feedsError == null) {
                            item(key = "feeds-empty") {
                                Box(
                                    Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("暂无动态", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
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

@Composable
private fun ProfileHeader(profile: UserProfile, isOwnPage: Boolean, onLogout: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            UserAvatar(profile.avatarUrl, profile.username, 76.dp)
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
        }
        profile.bio?.toPlainText()?.takeIf(String::isNotBlank)?.let { bio ->
            Spacer(Modifier.height(16.dp))
            Text(bio, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ProfileMetric("动态", profile.feedCount, Modifier.weight(1f))
            ProfileMetric("关注", profile.followingCount, Modifier.weight(1f))
            ProfileMetric("粉丝", profile.followerCount, Modifier.weight(1f))
        }
        if (isOwnPage) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onLogout, modifier = Modifier.align(Alignment.End)) {
                Text("退出登录")
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun ProfileMetric(label: String, value: Int?, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value?.formatCompact() ?: "--",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
