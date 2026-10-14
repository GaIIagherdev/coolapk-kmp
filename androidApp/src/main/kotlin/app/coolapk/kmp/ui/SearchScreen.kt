package app.coolapk.kmp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.coolapk.kmp.core.SearchCategory
import com.coolapk.kmp.core.SearchResult
import com.coolapk.kmp.core.SearchResultKind
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenResult: (SearchResult) -> Unit,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var input by rememberSaveable { mutableStateOf(state.query) }
    val lists = remember(state.query) { mutableStateMapOf<SearchCategory, LazyListState>() }
    val listState = lists.getOrPut(state.category) { LazyListState() }
    val categories = SearchCategory.entries
    val active = state.activeTab
    val submit: () -> Unit = { if (input.isNotBlank()) viewModel.search(input) }
    val openResult: (SearchResult) -> Unit = { result ->
        focusManager.clearFocus()
        keyboardController?.hide()
        onOpenResult(result)
    }

    BackHandler(onBack = onBack)
    LaunchedEffect(listState, state.query, state.category, active.items.size, active.hasMore) {
        snapshotFlow {
            val layout = listState.layoutInfo
            layout.visibleItemsInfo.lastOrNull()?.index?.let { it >= layout.totalItemsCount - 3 } ?: false
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd && active.items.isNotEmpty()) viewModel.loadMore()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding()
                    .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                }
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("搜索酷安内容") },
                    trailingIcon = {
                        IconButton(onClick = submit, enabled = input.isNotBlank()) {
                            Icon(Icons.Rounded.Search, contentDescription = "执行搜索")
                        }
                    },
                    shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit() }),
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.query.isNotBlank()) {
                SecondaryScrollableTabRow(
                    selectedTabIndex = categories.indexOf(state.category),
                    edgePadding = 12.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) },
                ) {
                    categories.forEach { category ->
                        Tab(
                            selected = state.category == category,
                            onClick = { viewModel.selectCategory(category) },
                            text = { Text(category.label, maxLines = 1) },
                        )
                    }
                }
                if (state.category == SearchCategory.ASK) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("all" to "全部", "question" to "提问", "answer" to "回答").forEach { (key, label) ->
                            FilterChip(
                                selected = state.askFeedType == key,
                                onClick = { viewModel.selectAskFeedType(key) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    when {
                        active.loading && active.items.isEmpty() -> ExpressiveLoadingIndicator(Modifier.align(Alignment.Center))
                        active.error != null && active.items.isEmpty() -> SearchMessage(
                            title = "搜索失败",
                            detail = active.error,
                            action = "重试",
                            onAction = viewModel::retry,
                            modifier = Modifier.align(Alignment.Center),
                        )
                        active.items.isEmpty() -> SearchMessage(
                            title = "未找到相关结果",
                            detail = "试试其他关键词或切换分类",
                            modifier = Modifier.align(Alignment.Center),
                        )
                        else -> LazyColumn(
                            state = listState,
                            modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            items(active.items, key = { "${it.kind}:${it.id}" }) { item ->
                                SearchResultRow(item, openResult)
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 88.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                                )
                            }
                            item(key = "footer") {
                                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                    when {
                                        active.loading -> ExpressiveLoadingIndicator(Modifier.size(24.dp))
                                        active.error != null -> TextButton(onClick = viewModel::loadMore) { Text("加载失败，重试") }
                                        !active.hasMore -> Text("没有更多结果", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    SearchHotWords(
                        words = state.hotWords,
                        loading = state.hotLoading,
                        error = state.hotError,
                        onRefresh = { viewModel.loadHotWords(refresh = true) },
                        onSelect = { word -> input = word; viewModel.search(word) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchHotWords(
    words: List<String>,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.widthIn(max = 900.dp).fillMaxWidth().fillMaxHeight().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("热门搜索", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onRefresh, enabled = !loading) {
                if (loading) ExpressiveLoadingIndicator(size = 18.dp)
                else Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("换一批")
            }
        }
        when {
            loading && words.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ExpressiveLoadingIndicator()
            }
            words.isEmpty() -> SearchMessage(
                title = "开始搜索",
                detail = error ?: "输入关键词查找酷安内容",
                action = if (error != null) "重试" else null,
                onAction = onRefresh,
                modifier = Modifier.fillMaxSize(),
            )
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(words.size) { index ->
                    val word = words[index]
                    ListItem(
                        modifier = Modifier.clickable(role = Role.Button, onClickLabel = "搜索$word") { onSelect(word) },
                        leadingContent = {
                            Surface(
                                modifier = Modifier.size(36.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = if (index < 3) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        (index + 1).toString(),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (index < 3) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                    ) { Text(word, style = MaterialTheme.typography.bodyLarge) }
                    if (index < words.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 68.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(item: SearchResult, onOpenResult: (SearchResult) -> Unit) {
    val navigable = item.targetId != null && item.kind in setOf(
        SearchResultKind.APP, SearchResultKind.USER, SearchResultKind.FEED,
        SearchResultKind.PRODUCT, SearchResultKind.TOPIC,
    )
    ListItem(
        modifier = Modifier.then(if (navigable) Modifier.clickable(role = Role.Button, onClickLabel = "打开${item.title}") {
            onOpenResult(item)
        } else Modifier),
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.kind.label, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                item.subtitle?.let {
                    Text(it.toPlainText(), maxLines = 2, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        leadingContent = {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(item.kind.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    item.imageUrl?.let { url ->
                        AsyncImage(
                            model = coolapkImageRequest(url),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        },
        trailingContent = if (navigable) {{
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
        }} else null,
    ) {
        Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium)
    }
}

private val SearchResultKind.label: String get() = when (this) {
    SearchResultKind.APP -> "应用"
    SearchResultKind.USER -> "用户"
    SearchResultKind.FEED -> "动态"
    SearchResultKind.QUESTION -> "问题"
    SearchResultKind.PRODUCT -> "数码"
    SearchResultKind.TOPIC -> "话题"
    SearchResultKind.OTHER -> "其他"
}

private val SearchResultKind.icon get() = when (this) {
    SearchResultKind.APP -> Icons.Rounded.Apps
    SearchResultKind.USER -> Icons.Rounded.Person
    SearchResultKind.FEED, SearchResultKind.QUESTION -> Icons.Rounded.ChatBubbleOutline
    SearchResultKind.PRODUCT -> Icons.Rounded.Devices
    SearchResultKind.TOPIC -> Icons.Rounded.Tag
    SearchResultKind.OTHER -> Icons.Rounded.Search
}

@Composable
private fun SearchMessage(
    title: String,
    detail: String?,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (!detail.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}
