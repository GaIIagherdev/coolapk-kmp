package app.coolapk.kmp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import com.coolapk.kmp.core.FollowedEntry

/** 关注的论坛与话题两个标签。字段未经真实样本验证。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FollowedScreen(
    uid: String,
    onBack: () -> Unit,
    viewModel: FollowedViewModel = viewModel(key = "followed-$uid", factory = FollowedViewModel.factory(uid)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("关注的论坛与话题") },
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
            val tabs = FollowedTab.entries
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
            val list = if (state.tab == FollowedTab.FORUMS) state.forums else state.topics
            PagedColumn(
                list = list,
                emptyTitle = if (state.tab == FollowedTab.FORUMS) "还没有关注的论坛" else "还没有关注的话题",
                emptyDetail = "在酷安关注后会显示在这里。",
                key = { it.id },
                onLoadMore = viewModel::loadMore,
                onRetry = viewModel::retry,
            ) { entry ->
                EntryRow(entry)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryRow(entry: FollowedEntry) {
    ListItem(
        headlineContent = {
            Text(
                entry.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            entry.subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        },
        leadingContent = {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                entry.imageUrl?.let { url ->
                    AsyncImage(
                        model = coolapkImageRequest(url),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.clip(RoundedCornerShape(20.dp)),
    )
}
