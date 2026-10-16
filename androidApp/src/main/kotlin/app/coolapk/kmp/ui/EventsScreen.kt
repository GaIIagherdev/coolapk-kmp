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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coolapk.kmp.core.ListEntry

/** 酷友圈活动列表。字段未经真实样本验证。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EventsScreen(
    onBack: () -> Unit,
    viewModel: EventsViewModel = viewModel(key = "events", factory = EventsViewModel.factory()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("活动") },
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
                emptyTitle = "暂时没有活动",
                emptyDetail = "酷友圈的活动会显示在这里。",
                key = { it.id },
                onLoadMore = viewModel::loadMore,
                onRetry = viewModel::retry,
            ) { entry: ListEntry ->
                EntryRow(entry)
            }
        }
    }
}
