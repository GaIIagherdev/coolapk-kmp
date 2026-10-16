package app.coolapk.kmp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coolapk.kmp.core.HistoryEntry

/** 浏览历史：与参考项目一致展示点击过的动态，点击打开动态详情。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryScreen(
    onBack: () -> Unit,
    onOpenFeed: (String) -> Unit,
    viewModel: HistoryViewModel = viewModel(key = "history", factory = HistoryViewModel.factory()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("浏览历史") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PagedColumn(
                list = state,
                emptyTitle = "还没有浏览记录",
                emptyDetail = "浏览过的动态会显示在这里。",
                key = { it.id },
                onLoadMore = viewModel::loadMore,
                onRetry = viewModel::retry,
            ) { entry: HistoryEntry ->
                HistoryRow(entry, onOpen = onOpenFeed)
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onOpen: (String) -> Unit) {
    EntryRow(
        entry = com.coolapk.kmp.core.ListEntry(
            id = entry.id,
            title = entry.title,
            subtitle = entry.description,
            imageUrl = entry.imageUrl,
        ),
        onClick = entry.feedId?.let { feedId -> { onOpen(feedId) } },
    )
}
