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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.coolapk.kmp.core.DigitalEntity
import com.coolapk.kmp.core.FeedItem

/** 我的装备：全部 / 想买 / 买过（动态）、商品店铺、产品专辑。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MyGoodsScreen(
    uid: String,
    onBack: () -> Unit,
    onOpenFeed: (FeedItem) -> Unit,
    onOpenProduct: (String) -> Unit,
    onLike: (FeedItem) -> Unit,
    likeOverrides: Map<String, Boolean>,
    viewModel: MyGoodsViewModel = viewModel(key = "my-goods-$uid", factory = MyGoodsViewModel.factory(uid)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("我的装备") },
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
            val kinds = MyGoodsKind.entries
            SecondaryScrollableTabRow(
                selectedTabIndex = kinds.indexOf(state.kind),
                modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth(),
                edgePadding = 16.dp,
            ) {
                kinds.forEach { kind ->
                    Tab(
                        selected = state.kind == kind,
                        onClick = { viewModel.select(kind) },
                        text = { Text(kind.label, maxLines = 1) },
                    )
                }
            }
            MyGoodsList(
                kind = state.kind,
                list = state.current(),
                onLoadMore = viewModel::loadMore,
                onRetry = viewModel::retry,
                onOpenFeed = onOpenFeed,
                onOpenProduct = onOpenProduct,
                onLike = onLike,
                likeOverrides = likeOverrides,
            )
        }
    }
}

@Composable
private fun MyGoodsList(
    kind: MyGoodsKind,
    list: MyGoodsListState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpenFeed: (FeedItem) -> Unit,
    onOpenProduct: (String) -> Unit,
    onLike: (FeedItem) -> Unit,
    likeOverrides: Map<String, Boolean>,
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
            title = "这里还空空的",
            detail = "在酷安产品页添加想买或买过的装备后会显示在这里。",
            modifier = Modifier.fillMaxWidth(),
        )
        else -> LazyColumn(
            modifier = Modifier.widthIn(max = 900.dp).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(list.items, key = { _, item -> "${kind.key}-${item.id}" }) { _, entity ->
                val feed = entity.feed
                if (feed != null) {
                    FeedCard(
                        item = feed,
                        liked = likeOverrides[feed.id] ?: feed.isLiked,
                        onLike = { onLike(feed) },
                        onOpenDetail = { onOpenFeed(feed) },
                        onReply = { onOpenFeed(feed) },
                        onOpenImage = { _, _ -> },
                        card = true,
                    )
                } else {
                    GoodsRow(entity, onOpenProduct)
                }
            }
            if (list.loading) item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    ExpressiveLoadingIndicator()
                }
            }
            if (!list.loading && list.hasMore && list.items.isNotEmpty()) item(key = "more") {
                Text(
                    "加载更多",
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onLoadMore).padding(16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** 商品店铺、产品专辑等非动态条目：封面 + 标题 + 副标题，产品可进入详情。 */
@Composable
private fun GoodsRow(entity: DigitalEntity, onOpenProduct: (String) -> Unit) {
    val isProduct = entity.entityType.equals("product", true)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(enabled = isProduct) { onOpenProduct(entity.id) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            entity.imageUrl?.let { url ->
                AsyncImage(
                    model = coolapkImageRequest(url),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(6.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entity.title ?: "未命名",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            entity.subTitle?.takeIf(String::isNotBlank)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
