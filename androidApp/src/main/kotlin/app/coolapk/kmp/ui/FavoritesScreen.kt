package app.coolapk.kmp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.coolapk.kmp.core.FeedCollection
import com.coolapk.kmp.core.FeedItem

/** 收藏：收藏的动态与收藏单两个标签；进入收藏单后展示其中的内容。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FavoritesScreen(
    onBack: () -> Unit,
    onOpenFeed: (FeedItem) -> Unit,
    onLike: (FeedItem) -> Unit,
    likeOverrides: Map<String, Boolean>,
    viewModel: FavoritesViewModel = viewModel(key = "favorites", factory = FavoritesViewModel.factory()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val goBack: () -> Unit = { if (!viewModel.closeCollection()) onBack() }
    BackHandler(onBack = goBack)
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state.openedCollection == null && state.tab == FavoritesTab.COLLECTIONS) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::openCreateEditor,
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text("新建收藏单") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(state.openedCollection?.title ?: "收藏", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = goBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    state.openedCollection?.let { collection ->
                        IconButton(onClick = { viewModel.openEditEditor(collection) }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "编辑收藏单")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
            val opened = state.openedCollection
            if (opened == null) {
                val tabs = FavoritesTab.entries
                SecondaryScrollableTabRow(
                    selectedTabIndex = tabs.indexOf(state.tab),
                    modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth(),
                    edgePadding = 16.dp,
                ) {
                    tabs.forEach { tab ->
                        Tab(
                            selected = state.tab == tab,
                            onClick = { viewModel.select(tab) },
                            text = { Text(tab.label, maxLines = 1) },
                        )
                    }
                }
            }
            when {
                opened != null -> PagedFeedList(
                    list = state.collectionItems,
                    emptyDetail = "在酷安收藏这条动态并选择「${opened.title}」后会显示在这里。",
                    onLoadMore = viewModel::loadMore,
                    onRetry = viewModel::retry,
                    onOpenFeed = onOpenFeed,
                    onLike = onLike,
                    likeOverrides = likeOverrides,
                    onRemoveFromCollection = viewModel::removeFromCollection,
                )
                state.tab == FavoritesTab.FEEDS -> PagedFeedList(
                    list = state.feeds,
                    emptyDetail = "在酷安收藏动态后会显示在这里。",
                    onLoadMore = viewModel::loadMore,
                    onRetry = viewModel::retry,
                    onOpenFeed = onOpenFeed,
                    onLike = onLike,
                    likeOverrides = likeOverrides,
                )
                else -> CollectionList(
                    list = state.collections,
                    onLoadMore = viewModel::loadMore,
                    onRetry = viewModel::retry,
                    onOpen = viewModel::openCollection,
                    onEdit = viewModel::openEditEditor,
                    onDelete = viewModel::askDelete,
                )
            }
        }
    }

    state.editor?.let { draft ->
        CollectionEditorDialog(
            draft = draft,
            error = state.editorError,
            submitting = state.submitting,
            onTitleChange = viewModel::updateDraftTitle,
            onDescriptionChange = viewModel::updateDraftDescription,
            onOpenChange = viewModel::setDraftOpen,
            onDismiss = viewModel::dismissEditor,
            onConfirm = viewModel::submitEditor,
        )
    }

    state.pendingDelete?.let { collection ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("删除收藏单？") },
            text = { Text("「${collection.title}」会被删除，其中的内容仍保留在酷安，只是不再属于这个收藏单。") },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) { Text("取消") }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
}

@Composable
private fun PagedFeedList(
    list: PagedListState<FeedItem>,
    emptyDetail: String,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpenFeed: (FeedItem) -> Unit,
    onLike: (FeedItem) -> Unit,
    likeOverrides: Map<String, Boolean>,
    onRemoveFromCollection: ((FeedItem) -> Unit)? = null,
) {
    PagedColumn(
        list = list,
        emptyTitle = "这里还空空的",
        emptyDetail = emptyDetail,
        key = { it.id },
        onLoadMore = onLoadMore,
        onRetry = onRetry,
    ) { feed ->
        Column {
            FeedCard(
                item = feed,
                liked = likeOverrides[feed.id] ?: feed.isLiked,
                onLike = { onLike(feed) },
                onOpenDetail = { onOpenFeed(feed) },
                onReply = { onOpenFeed(feed) },
                onOpenImage = { _, _ -> },
                card = true,
            )
            if (onRemoveFromCollection != null && feed.collectionItemId != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(end = 4.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { onRemoveFromCollection(feed) }) {
                        Icon(
                            Icons.Rounded.RemoveCircleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("移出收藏单")
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionList(
    list: PagedListState<FeedCollection>,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (FeedCollection) -> Unit,
    onEdit: (FeedCollection) -> Unit,
    onDelete: (FeedCollection) -> Unit,
) {
    PagedColumn(
        list = list,
        emptyTitle = "还没有收藏单",
        emptyDetail = "点右下角「新建收藏单」，把想留着的动态归到一起。",
        key = { it.id },
        onLoadMore = onLoadMore,
        onRetry = onRetry,
        bottomPadding = 88.dp,
    ) { collection ->
        CollectionRow(collection, onOpen, onEdit, onDelete)
    }
}

/** 通用分页容器：处理首次加载、错误、空态、加载更多。 */
@Composable
internal fun <T> PagedColumn(
    list: PagedListState<T>,
    emptyTitle: String,
    emptyDetail: String,
    key: (T) -> Any,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    bottomPadding: Dp = 8.dp,
    row: @Composable (T) -> Unit,
) {
    when {
        list.loading && list.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ExpressiveLoadingIndicator()
        }
        list.error != null && list.items.isEmpty() -> CoolapkErrorState(
            message = list.error,
            onRetry = onRetry,
            modifier = Modifier.fillMaxWidth(),
        )
        list.page > 0 && list.items.isEmpty() -> CoolapkEmptyState(
            title = emptyTitle,
            detail = emptyDetail,
            modifier = Modifier.fillMaxWidth(),
        )
        else -> LazyColumn(
            modifier = Modifier.widthIn(max = 900.dp).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(list.items, key = key) { row(it) }
            if (list.loading) item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    ExpressiveLoadingIndicator()
                }
            }
            if (!list.loading && list.hasMore && list.items.isNotEmpty()) item(key = "more") {
                Text(
                    "加载更多",
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onLoadMore).padding(16.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionRow(
    collection: FeedCollection,
    onOpen: (FeedCollection) -> Unit,
    onEdit: (FeedCollection) -> Unit,
    onDelete: (FeedCollection) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = {
            Text(
                collection.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column {
                collection.description.takeIf(String::isNotBlank)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    buildString {
                        append("${collection.itemCount} 条内容")
                        if (collection.likeCount > 0) append(" · ${collection.likeCount} 赞")
                        if (collection.isDefault) append(" · 默认")
                        if (!collection.isOpen) append(" · 私密")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        leadingContent = {
            Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                collection.coverUrl?.let { url ->
                    AsyncImage(
                        model = coolapkImageRequest(url),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "收藏单操作")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onEdit(collection)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = {
                            menuOpen = false
                            onDelete(collection)
                        },
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onOpen(collection) },
    )
}

@Composable
private fun CollectionEditorDialog(
    draft: CollectionDraft,
    error: String?,
    submitting: Boolean,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onOpenChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.isNew) "新建收藏单" else "编辑收藏单") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = onTitleChange,
                    label = { Text("标题") },
                    singleLine = true,
                    enabled = !submitting,
                    supportingText = { Text("${draft.title.length}/$CollectionTitleMaxLength") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.description,
                    onValueChange = onDescriptionChange,
                    label = { Text("简介") },
                    minLines = 3,
                    maxLines = 5,
                    enabled = !submitting,
                    supportingText = { Text("${draft.description.length}/$CollectionDescriptionMaxLength") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("公开收藏单", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "关闭后仅自己可见",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = draft.isOpen, onCheckedChange = onOpenChange, enabled = !submitting)
                }
                error?.takeIf(String::isNotBlank)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) { Text("取消") }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = draft.canSubmit && !submitting) {
                Text(
                    when {
                        submitting -> "保存中…"
                        draft.isNew -> "创建"
                        else -> "保存"
                    },
                )
            }
        },
    )
}
