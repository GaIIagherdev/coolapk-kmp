package app.coolapk.kmp.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import com.coolapk.kmp.core.DigitalProductDetail
import com.coolapk.kmp.core.FeedItem
import com.coolapk.kmp.core.ProductRatingChart
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DigitalProductDetailScreen(
    productId: String,
    viewModel: DigitalProductDetailViewModel,
    onBack: () -> Unit,
    onOpenImage: (List<String>, Int) -> Unit,
    onOpenFeed: (FeedItem) -> Unit = {},
    onLike: (FeedItem) -> Unit = {},
    likeOverrides: Map<String, Boolean> = emptyMap(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(productId) { viewModel.load(productId) }
    BackHandler(onBack = onBack)
    var tab by rememberSaveable { mutableStateOf(ProductDetailTab.OVERVIEW) }
    var writingRating by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(tab, state.productId) {
        when (tab) {
            ProductDetailTab.OVERVIEW -> Unit
            ProductDetailTab.RATINGS -> viewModel.openRatings()
            ProductDetailTab.DISCUSS -> viewModel.openDiscussions()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.detail?.title ?: "产品详情", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading && state.detail == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                ExpressiveLoadingIndicator()
            }

            state.detail == null -> ProductDetailError(
                message = state.error ?: "产品详情暂不可用",
                onRetry = viewModel::retry,
                modifier = Modifier.fillMaxSize().padding(padding),
            )

            else -> {
                val detail = state.detail ?: return@Scaffold
                Column(Modifier.fillMaxSize().padding(padding)) {
                    ProductActionBar(
                        wish = state.wish,
                        follow = state.follow,
                        onWish = viewModel::toggleWish,
                        onFollow = viewModel::toggleFollow,
                    )
                    state.actionError?.let { message ->
                        Text(
                            message,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                        ProductDetailTab.entries.forEach { entry ->
                            Tab(
                                selected = tab == entry,
                                onClick = { tab = entry },
                                text = { Text(entry.label) },
                            )
                        }
                    }
                    when (tab) {
                        ProductDetailTab.OVERVIEW -> ProductDetailBody(
                            detail = detail,
                            loading = state.loading,
                            error = state.error,
                            onRetry = viewModel::retry,
                            onOpenImage = onOpenImage,
                            chart = state.chart,
                            chartPeriod = state.chartPeriod,
                            onChartPeriod = viewModel::selectChartPeriod,
                            modifier = Modifier.fillMaxSize(),
                        )
                        ProductDetailTab.RATINGS -> ProductRatingsTab(
                            chart = state.chart,
                            chartPeriod = state.chartPeriod,
                            onChartPeriod = viewModel::selectChartPeriod,
                            ratings = state.ratings,
                            selectedStar = state.ratingStar,
                            onStar = viewModel::setRatingStar,
                            onLoadMore = viewModel::loadMoreRatings,
                            onRetry = viewModel::openRatings,
                            onWrite = { writingRating = true },
                            modifier = Modifier.fillMaxSize(),
                        )
                        ProductDetailTab.DISCUSS -> ProductDiscussTab(
                            discussions = state.discussions,
                            likeOverrides = likeOverrides,
                            onOpenFeed = onOpenFeed,
                            onLike = onLike,
                            onOpenImage = onOpenImage,
                            onLoadMore = viewModel::loadMoreDiscussions,
                            onRetry = viewModel::openDiscussions,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                if (writingRating) {
                    ProductRatingDialog(
                        submitting = state.ratingSubmitting,
                        error = state.actionError,
                        onDismiss = { writingRating = false },
                        onSubmit = { score, message, bought ->
                            viewModel.submitRating(score, message, bought) { writingRating = false }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductDetailError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.Devices, contentDescription = null, modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Rounded.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("重试")
        }
    }
}

@Composable
private fun ProductDetailBody(
    detail: DigitalProductDetail,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onOpenImage: (List<String>, Int) -> Unit,
    chart: ProductRatingChart?,
    chartPeriod: String,
    onChartPeriod: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val images = (listOfNotNull(detail.logoUrl) + detail.imageUrls).distinct()
    BoxWithConstraints(modifier) {
        val expanded = maxWidth >= 760.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (loading) item(key = "loading") {
                ExpressiveLoadingIndicator(Modifier.size(24.dp))
            }
            if (error != null) item(key = "error") {
                ProductDetailError(error, onRetry, Modifier.widthIn(max = 900.dp).fillMaxWidth())
            }
            if (chart != null && chart.series(chartPeriod).isNotEmpty()) item(key = "trend") {
                Column(Modifier.widthIn(max = 900.dp).fillMaxWidth()) {
                    SectionHeader("评分趋势", horizontalPadding = 0.dp)
                    ProductRatingChartView(chart, chartPeriod, onChartPeriod, Modifier.padding(horizontal = 0.dp))
                }
            }
            item(key = "overview") {
                val media: @Composable (Modifier) -> Unit = { mediaModifier ->
                    ProductImage(
                        url = images.firstOrNull(),
                        description = "${detail.title}的产品图片",
                        onClick = images.takeIf { it.isNotEmpty() }?.let { { onOpenImage(images, 0) } },
                        modifier = mediaModifier,
                    )
                }
                if (expanded) {
                    Row(
                        modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        media(Modifier.size(320.dp))
                        ProductOverview(detail, Modifier.weight(1f))
                    }
                } else {
                    Column(
                        modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        media(Modifier.fillMaxWidth().aspectRatio(1f))
                        ProductOverview(detail)
                    }
                }
            }
            if (detail.specs.isNotEmpty()) item(key = "specs") {
                Column(Modifier.widthIn(max = 900.dp).fillMaxWidth()) {
                    SectionHeader("产品规格", horizontalPadding = 0.dp)
                    GroupedSurface {
                        detail.specs.forEachIndexed { index, spec ->
                            if (index > 0) GroupedDivider()
                            Text(
                                spec,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
            if (detail.imageUrls.isNotEmpty()) item(key = "gallery") {
                Column(Modifier.widthIn(max = 900.dp).fillMaxWidth()) {
                    SectionHeader("产品图片", horizontalPadding = 0.dp)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        itemsIndexed(detail.imageUrls) { index, url ->
                            ProductImage(
                                url = url,
                                description = "查看第 ${index + 1} 张产品图片",
                                onClick = { onOpenImage(images, images.indexOf(url)) },
                                modifier = Modifier.size(128.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductOverview(detail: DigitalProductDetail, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            detail.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val category = listOfNotNull(detail.brand, detail.category).joinToString(" · ")
        if (category.isNotBlank()) {
            Text(category, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (detail.score != null || detail.hotCount != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                detail.score?.let { score ->
                    Column(Modifier.weight(1f)) {
                        Text(String.format(Locale.ROOT, "%.1f", score),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary)
                        Text(detail.ratingCount?.let { "酷友评分 · $it 人" } ?: "酷友评分",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                detail.hotCount?.let { hot ->
                    Column(Modifier.weight(1f)) {
                        Text(hot, style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold)
                        Text("热度", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        detail.description?.takeIf(String::isNotBlank)?.let { description ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(description, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProductImage(
    url: String?,
    description: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        if (url == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Devices, contentDescription = "暂无产品图片", modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary)
            }
        } else {
            SubcomposeAsyncImage(
                model = coolapkImageRequest(url),
                contentDescription = description,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ExpressiveLoadingIndicator(Modifier.size(24.dp))
                    }
                },
                error = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Devices, contentDescription = "图片加载失败",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
            )
        }
    }
}
