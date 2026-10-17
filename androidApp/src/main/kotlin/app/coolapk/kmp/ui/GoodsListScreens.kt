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
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.coolapk.kmp.core.HomeRepository
import com.coolapk.kmp.core.ListEntry
import com.coolapk.kmp.core.createPlatformGateway

/** 好物清单列表：当前账号的万物清单，点击进入清单详情。字段未经真实样本验证。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GoodsListsScreen(
    uid: String,
    onBack: () -> Unit,
    onOpenList: (ListEntry) -> Unit,
    viewModel: EntryListViewModel = viewModel(
        key = "goods-lists-$uid",
        factory = viewModelFactory { initializer { EntryListViewModel { page -> HomeRepository(createPlatformGateway()).loadGoodsLists(uid, page) } } },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("好物清单") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PagedColumn(
                list = state,
                emptyTitle = "还没有好物清单",
                emptyDetail = "在酷安创建万物清单后会显示在这里。",
                key = { it.id },
                onLoadMore = viewModel::loadMore,
                onRetry = viewModel::retry,
            ) { entry: ListEntry ->
                EntryRow(entry, onClick = { onOpenList(entry) })
            }
        }
    }
}

/** 好物清单详情：清单中的条目列表。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GoodsListDetailScreen(
    uid: String,
    listId: String,
    title: String,
    onBack: () -> Unit,
    viewModel: EntryListViewModel = viewModel(
        key = "goods-list-$uid-$listId",
        factory = viewModelFactory { initializer { EntryListViewModel { page -> HomeRepository(createPlatformGateway()).loadGoodsListItems(uid, listId, page) } } },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PagedColumn(
                list = state,
                emptyTitle = "这个清单还没有内容",
                emptyDetail = "向清单中添加好物后会显示在这里。",
                key = { it.id },
                onLoadMore = viewModel::loadMore,
                onRetry = viewModel::retry,
            ) { entry: ListEntry ->
                EntryRow(entry)
            }
        }
    }
}
