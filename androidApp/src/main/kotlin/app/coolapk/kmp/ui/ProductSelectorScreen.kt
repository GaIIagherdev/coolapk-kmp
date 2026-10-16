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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.coolapk.kmp.core.DigitalProductDetail
import com.coolapk.kmp.core.SearchResult

/** 选机对比：搜索产品，最多选三款并排对比规格。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProductSelectorScreen(
    onBack: () -> Unit,
    onCompare: (List<String>, List<String>) -> Unit = { _, _ -> },
    viewModel: ProductSelectorViewModel = viewModel(factory = ProductSelectorViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    LaunchedEffect(state.compareConfigIds) {
        state.compareConfigIds?.let { ids ->
            val titles = state.compareTitles
            viewModel.consumeCompare()
            onCompare(ids, titles)
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("选机对比") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 900.dp).fillMaxWidth().padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::updateQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("搜索机型") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        TextButton(onClick = viewModel::search, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("搜索")
                        }
                    },
                )
                if (state.selected.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    CompareColumns(state.selected, state.details, onRemove = viewModel::toggle)
                }
                if (state.selected.size >= 2) {
                    Button(
                        onClick = viewModel::startCompare,
                        enabled = !state.comparing,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp),
                    ) {
                        Text(if (state.comparing) "读取配置中" else "开始对比")
                    }
                }
                state.compareError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp))
                }
                Spacer(Modifier.height(12.dp))
            }
            SearchResults(
                state = state,
                onToggle = viewModel::toggle,
                onLoadMore = viewModel::loadMore,
                onRetry = viewModel::search,
            )
        }
    }
}

@Composable
private fun SearchResults(
    state: ProductSelectorUiState,
    onToggle: (SearchResult) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    when {
        state.loading && state.results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ExpressiveLoadingIndicator()
        }
        state.error != null && state.results.isEmpty() -> CoolapkErrorState(
            message = state.error,
            onRetry = onRetry,
            modifier = Modifier.fillMaxWidth(),
        )
        state.page > 0 && state.results.isEmpty() -> CoolapkEmptyState(
            title = "没有找到匹配机型",
            detail = "可以换个关键词再试试。",
            modifier = Modifier.fillMaxWidth(),
        )
        state.page == 0 && state.selected.isEmpty() -> CoolapkEmptyState(
            title = "搜索机型开始对比",
            detail = "最多同时对比 $MAX_COMPARE_PRODUCTS 款产品。",
            modifier = Modifier.fillMaxWidth(),
        )
        else -> LazyColumn(
            modifier = Modifier.widthIn(max = 900.dp).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(state.results, key = { _, item -> item.id }) { _, product ->
                ProductResultRow(
                    product = product,
                    selected = state.selected.any { it.id == product.id },
                    onToggle = { onToggle(product) },
                )
            }
            if (state.loading) item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    ExpressiveLoadingIndicator()
                }
            }
            if (!state.loading && state.hasMore && state.results.isNotEmpty()) item(key = "more") {
                TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("加载更多")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductResultRow(product: SearchResult, selected: Boolean, onToggle: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(product.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            product.subtitle?.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        leadingContent = {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                product.imageUrl?.let { url ->
                    AsyncImage(
                        model = coolapkImageRequest(url),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        contentScale = ContentScale.Fit,
                    )
                } ?: Icon(Icons.Rounded.Devices, contentDescription = null)
            }
        },
        trailingContent = {
            FilterChip(
                selected = selected,
                onClick = onToggle,
                label = { Text(if (selected) "已加入" else "加入对比") },
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onToggle),
    )
}

/** 并排对比：每款产品一列，展示图片、品牌、评分、热度与规格。 */
@Composable
private fun CompareColumns(
    selected: List<SearchResult>,
    details: Map<String, DigitalProductDetail?>,
    onRemove: (SearchResult) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        selected.forEach { product ->
            val productId = product.targetId ?: product.id
            val detail = details[productId]
            Column(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    detail?.title ?: product.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                )
                val score = detail?.score
                Text(
                    if (score != null && score > 0) "酷友评分 ${"%.1f".format(score)}" else "暂无评分",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                detail?.hotCount?.let {
                    Text("热度 $it", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                when {
                    detail == null && productId in details -> Text(
                        "详情加载失败",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    detail == null -> ExpressiveLoadingIndicator(Modifier.size(24.dp))
                    detail.specs.isEmpty() -> Text(
                        "暂无规格",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> detail.specs.take(8).forEach { spec ->
                        Text(
                            spec,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
                TextButton(onClick = { onRemove(product) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("移出")
                }
            }
        }
    }
}
