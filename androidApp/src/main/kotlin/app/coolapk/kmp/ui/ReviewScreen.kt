package app.coolapk.kmp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coolapk.kmp.core.FeedItem

/** 评测区：按数码测评、数码、手机等板块浏览动态。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReviewScreen(
    onBack: () -> Unit,
    onOpenFeed: (FeedItem) -> Unit,
    onLike: (FeedItem) -> Unit,
    likeOverrides: Map<String, Boolean>,
    viewModel: ReviewViewModel = viewModel(key = "reviews", factory = ReviewViewModel.factory()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("评测区") },
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
            val boards = ReviewBoard.entries
            SecondaryScrollableTabRow(
                selectedTabIndex = boards.indexOf(state.board),
                modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth(),
                edgePadding = 16.dp,
            ) {
                boards.forEach { board ->
                    Tab(
                        selected = state.board == board,
                        onClick = { viewModel.select(board) },
                        text = { Text(board.label, maxLines = 1) },
                    )
                }
            }
            state.notice?.let { notice ->
                Text(
                    notice,
                    modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PagedColumn(
                list = state.list,
                emptyTitle = "这个板块还没有内容",
                emptyDetail = "换一个板块看看。",
                key = { it.id },
                onLoadMore = viewModel::loadMore,
                onRetry = viewModel::retry,
            ) { feed ->
                FeedCard(
                    item = feed,
                    liked = likeOverrides[feed.id] ?: feed.isLiked,
                    onLike = { onLike(feed) },
                    onOpenDetail = { onOpenFeed(feed) },
                    onReply = { onOpenFeed(feed) },
                    onOpenImage = { _, _ -> },
                    card = true,
                )
            }
        }
    }
}
