package app.coolapk.kmp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coolapk.kmp.core.FeedItem
import com.coolapk.kmp.core.ReplyItem
import com.coolapk.kmp.core.ReplySort
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedDetailScreen(
    feedId: String,
    preview: FeedItem?,
    focusComments: Boolean = false,
    onOpenImage: (List<String>, Int) -> Unit,
    onOpenAuthor: (String) -> Unit = {},
    onBack: () -> Unit,
    onFeedChanged: () -> Unit,
    viewModel: FeedDetailViewModel = viewModel(factory = FeedDetailViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val threadListState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val commentFocusGate = remember(feedId, focusComments) { CommentFocusGate() }
    var replyTarget by remember { mutableStateOf<ReplyItem?>(null) }
    var composerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(feedId) { viewModel.open(feedId, preview) }
    LaunchedEffect(state.interactionError) {
        state.interactionError?.let {
            snackbar.showSnackbar(it)
            viewModel.clearInteractionError()
        }
    }
    LaunchedEffect(feedId, focusComments, state.feedId, state.repliesPage, state.threadParent) {
        if (commentFocusGate.shouldFocus(
                requested = focusComments,
                activeFeed = state.feedId == feedId,
                repliesLoaded = state.repliesPage > 0,
                threadOpen = state.threadParent != null,
            )
        ) {
            listState.animateScrollToItem(2)
        }
    }
    LaunchedEffect(listState, state.repliesPage, state.repliesHasMore, state.threadParent) {
        if (state.threadParent != null) return@LaunchedEffect
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= listState.layoutInfo.totalItemsCount - 3
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd && state.replies.isNotEmpty()) viewModel.loadMoreReplies()
        }
    }
    LaunchedEffect(threadListState, state.threadPage, state.threadHasMore, state.threadParent) {
        if (state.threadParent == null) return@LaunchedEffect
        snapshotFlow {
            val lastVisible = threadListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= threadListState.layoutInfo.totalItemsCount - 3
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd && state.threadReplies.isNotEmpty()) viewModel.loadMoreThread()
        }
    }

    val handleBack = {
        if (state.threadParent != null) viewModel.closeThread() else onBack()
    }
    BackHandler(onBack = handleBack)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.threadParent == null) "动态详情" else "评论详情",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.threadParent == null) {
                        state.detail?.let { detail ->
                            IconButton(onClick = { shareText(context, detail) }) {
                                Icon(Icons.Rounded.Share, contentDescription = "分享动态")
                            }
                        }
                        IconButton(onClick = viewModel::refresh, enabled = !state.detailLoading) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "刷新详情")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.navigationBarsPadding(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { replyTarget = state.threadParent; composerOpen = true },
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.threadParent == null) "写评论" else "回复 ${state.threadParent?.username}")
                    }
                    if (state.threadParent == null) {
                        Spacer(Modifier.width(8.dp))
                        val detail = state.detail
                        val liked = state.feedLikeOverride ?: detail?.isLiked == true
                        FilledTonalIconButton(
                            onClick = { viewModel.toggleFeedLike(onFeedChanged) },
                            enabled = detail != null && !state.feedLiking,
                        ) {
                            Icon(
                                if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = if (liked) "取消点赞" else "点赞动态",
                                tint = if (liked) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (state.threadParent == null) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.widthIn(max = 860.dp).fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                ) {
                    item(key = "detail") {
                        Column {
                            if (state.detailLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                            when {
                                state.detail != null -> DetailBody(
                                    item = state.detail!!,
                                    liked = state.feedLikeOverride ?: state.detail!!.isLiked,
                                    onLike = { viewModel.toggleFeedLike(onFeedChanged) },
                                    onReply = { replyTarget = null; composerOpen = true },
                                    onOpenImage = onOpenImage,
                                    onOpenAuthor = onOpenAuthor,
                                )
                                state.detailLoading -> Box(
                                    Modifier.fillMaxWidth().height(220.dp),
                                    contentAlignment = Alignment.Center,
                                ) { ExpressiveLoadingIndicator() }
                            }
                            state.detailError?.let { error ->
                                InlineLoadError(error, onRetry = viewModel::loadDetail)
                            }
                        }
                    }
                    item(key = "section-separator") {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    item(key = "comments-header") {
                        SectionHeader(
                            title = "评论 ${state.detail?.replyCount?.formatCompact().orEmpty()}",
                            horizontalPadding = 0.dp,
                        )
                    }
                    item(key = "comment-filters") {
                        androidx.compose.foundation.lazy.LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 12.dp),
                        ) {
                            items(
                                listOf(
                                    ReplySort.LATEST to "默认",
                                    ReplySort.NEWEST to "最新",
                                    ReplySort.POPULAR to "热门",
                                ),
                            ) { (sort, label) ->
                                FilterChip(
                                    selected = !state.authorOnly && state.sort == sort,
                                    onClick = { viewModel.selectSort(sort) },
                                    label = { Text(label) },
                                )
                            }
                            item {
                                FilterChip(
                                    selected = state.authorOnly,
                                    onClick = { viewModel.setAuthorOnly(!state.authorOnly) },
                                    label = { Text("只看楼主") },
                                )
                            }
                        }
                    }
                    itemsIndexed(state.replies, key = { _, it -> "reply-${it.id}" }) { index, reply ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(groupedItemShape(index, state.replies.size))
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                        ) {
                            ReplyBody(
                                reply = reply,
                                liked = state.replyLikeOverrides[reply.id] ?: reply.isLiked,
                                onLike = { viewModel.toggleReplyLike(reply) },
                                onReply = { replyTarget = reply; composerOpen = true },
                                onOpenThread = { viewModel.openThread(reply) },
                                onOpenImage = onOpenImage,
                                onOpenAuthor = onOpenAuthor,
                            )
                            if (index < state.replies.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.align(Alignment.BottomStart),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                )
                            }
                        }
                    }
                    item(key = "replies-footer") {
                        RepliesFooter(
                            isLoading = state.repliesLoading,
                            error = state.repliesError,
                            isEmpty = state.replies.isEmpty(),
                            hasMore = state.repliesHasMore,
                            onRetry = { if (state.repliesPage == 0) viewModel.refresh() else viewModel.loadMoreReplies() },
                        )
                    }
                }
            } else {
                val parent = state.threadParent!!
                LazyColumn(
                    state = threadListState,
                    modifier = Modifier.widthIn(max = 860.dp).fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                ) {
                    item(key = "thread-parent") {
                        ReplyBody(
                            reply = parent,
                            liked = state.replyLikeOverrides[parent.id] ?: parent.isLiked,
                            onLike = { viewModel.toggleReplyLike(parent) },
                            onReply = { replyTarget = parent; composerOpen = true },
                            onOpenThread = null,
                            onOpenImage = onOpenImage,
                            onOpenAuthor = onOpenAuthor,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            "全部回复",
                            modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    itemsIndexed(state.threadReplies, key = { _, it -> "thread-${it.id}" }) { index, reply ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(groupedItemShape(index, state.threadReplies.size))
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                        ) {
                            ReplyBody(
                                reply = reply,
                                liked = state.replyLikeOverrides[reply.id] ?: reply.isLiked,
                                onLike = { viewModel.toggleReplyLike(reply) },
                                onReply = { replyTarget = reply; composerOpen = true },
                                onOpenThread = null,
                                onOpenImage = onOpenImage,
                                onOpenAuthor = onOpenAuthor,
                            )
                            if (index < state.threadReplies.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.align(Alignment.BottomStart),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                )
                            }
                        }
                    }
                    item(key = "thread-footer") {
                        RepliesFooter(
                            isLoading = state.threadLoading,
                            error = state.threadError,
                            isEmpty = state.threadReplies.isEmpty(),
                            hasMore = state.threadHasMore,
                            onRetry = viewModel::retryThread,
                        )
                    }
                }
            }
        }
    }

    if (composerOpen) {
        DetailReplyDialog(
            target = replyTarget,
            onDismiss = { composerOpen = false },
            onSubmit = { message, result ->
                viewModel.sendReply(replyTarget, message, onFeedChanged, result)
            },
        )
    }

    // 详情被验证码拦截时先让人机验证，通过后用令牌重试；取消则退回网页版兜底。
    state.captcha?.let { challenge ->
        CaptchaDialog(
            captchaId = challenge.captchaId,
            onVerified = viewModel::submitCaptcha,
            onDismissed = viewModel::dismissCaptcha,
        )
    }
}

internal class CommentFocusGate {
    private var focused = false

    fun shouldFocus(
        requested: Boolean,
        activeFeed: Boolean,
        repliesLoaded: Boolean,
        threadOpen: Boolean,
    ): Boolean {
        if (!requested || !activeFeed || !repliesLoaded || threadOpen || focused) return false
        focused = true
        return true
    }
}

@Composable
private fun DetailBody(
    item: FeedItem,
    liked: Boolean,
    onLike: () -> Unit,
    onReply: () -> Unit,
    onOpenImage: (List<String>, Int) -> Unit,
    onOpenAuthor: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp)) {
        Row(
            modifier = item.uid?.let { uid ->
                Modifier.clickable(role = Role.Button, onClickLabel = "查看${item.username}的主页") {
                    onOpenAuthor(uid)
                }
            } ?: Modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserAvatar(item.avatarUrl, item.username, 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.username, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val metadata = listOfNotNull(item.createdAt?.toDisplayTime(), item.device)
                    .filter(String::isNotBlank).joinToString(" · ")
                if (metadata.isNotBlank()) {
                    Text(
                        metadata,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item.title?.takeIf(String::isNotBlank)?.let {
            Spacer(Modifier.height(20.dp))
            Text(it.toPlainText(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        item.message.toPlainText().takeIf(String::isNotBlank)?.let {
            Spacer(Modifier.height(if (item.title.isNullOrBlank()) 20.dp else 10.dp))
            Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        if (item.imageUrls.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            FeedImages(item.imageUrls, onOpenImage)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onLike) {
                Icon(
                    if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(Modifier.width(6.dp))
                val count = (item.likeCount + (if (liked) 1 else 0) - (if (item.isLiked) 1 else 0))
                    .coerceAtLeast(0)
                Text(count.formatCompact())
            }
            TextButton(onClick = onReply) {
                Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(6.dp))
                Text(item.replyCount.formatCompact())
            }
        }
    }
}

@Composable
private fun ReplyBody(
    reply: ReplyItem,
    liked: Boolean,
    onLike: () -> Unit,
    onReply: () -> Unit,
    onOpenThread: (() -> Unit)?,
    onOpenImage: (List<String>, Int) -> Unit,
    onOpenAuthor: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        Row(
            modifier = reply.uid?.let { uid ->
                Modifier.clickable(role = Role.Button, onClickLabel = "查看${reply.username}的主页") {
                    onOpenAuthor(uid)
                }
            } ?: Modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserAvatar(reply.avatarUrl, reply.username, 38.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        reply.username,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (reply.isFeedAuthor) {
                        Spacer(Modifier.width(6.dp))
                        Text("楼主", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                val metadata = listOfNotNull(
                    reply.createdAt?.toDisplayTime(),
                    reply.floor?.let { "$it 楼" },
                ).joinToString(" · ")
                if (metadata.isNotBlank()) {
                    Text(
                        metadata,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        reply.replyToUsername?.let {
            Text(
                "回复 @$it",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(reply.message.toPlainText(), style = MaterialTheme.typography.bodyMedium)
        if (reply.imageUrls.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FeedImages(reply.imageUrls, onOpenImage)
        }
        if (onOpenThread != null && reply.previewReplies.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    reply.previewReplies.take(2).forEach { preview ->
                        Text(
                            "${preview.username}：${preview.message.toPlainText()}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onLike) {
                Icon(
                    if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                val count = (reply.likeCount + (if (liked) 1 else 0) - (if (reply.isLiked) 1 else 0))
                    .coerceAtLeast(0)
                Text(count.formatCompact())
            }
            TextButton(onClick = onReply) { Text("回复") }
            if (onOpenThread != null && reply.replyCount > 0) {
                TextButton(onClick = onOpenThread) { Text("查看 ${reply.replyCount.formatCompact()} 条回复") }
            }
        }
    }
}

@Composable
private fun RepliesFooter(
    isLoading: Boolean,
    error: String?,
    isEmpty: Boolean,
    hasMore: Boolean,
    onRetry: () -> Unit,
) {
    when {
        isLoading -> Box(
            Modifier.fillMaxWidth().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) { ExpressiveLoadingIndicator(Modifier.size(28.dp)) }
        error != null -> InlineLoadError(error, onRetry)
        isEmpty -> Text(
            "暂无评论",
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        hasMore -> TextButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("加载更多") }
        else -> Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun InlineLoadError(message: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            message,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onRetry) { Text("重试") }
    }
}

@Composable
private fun DetailReplyDialog(
    target: ReplyItem?,
    onDismiss: () -> Unit,
    onSubmit: (String, (String?) -> Unit) -> Unit,
) {
    var message by rememberSaveable(target?.id) { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(target?.let { "回复 ${it.username}" } ?: "写评论") },
        text = {
            Column {
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 8,
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
