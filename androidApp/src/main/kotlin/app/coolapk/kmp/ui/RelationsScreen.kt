package app.coolapk.kmp.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coolapk.kmp.core.UserSummary

/** 关注列表（全部 / 特别关注 / 互相关注）或粉丝列表。 */
enum class RelationKind(val title: String, val apiCategory: String?) {
    FOLLOW("关注", "follow"),
    SPECIAL("特别关注", "special"),
    FRIEND("互相关注", "friend"),
    FANS("粉丝", null),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RelationsScreen(
    uid: String,
    initialKind: RelationKind,
    onBack: () -> Unit,
    onOpenUser: (String) -> Unit,
    viewModel: RelationsViewModel = viewModel(
        key = "relations-$uid-${initialKind.name}",
        factory = RelationsViewModel.factory(uid, initialKind),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    val tabs = if (state.kind == RelationKind.FANS) listOf(RelationKind.FANS)
        else listOf(RelationKind.FOLLOW, RelationKind.SPECIAL, RelationKind.FRIEND)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(state.kind.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            if (tabs.size > 1) {
                SecondaryScrollableTabRow(
                    selectedTabIndex = tabs.indexOf(state.kind).coerceAtLeast(0),
                    modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth(),
                    edgePadding = 16.dp,
                ) {
                    tabs.forEach { kind ->
                        Tab(selected = state.kind == kind, onClick = { viewModel.select(kind) }, text = { Text(kind.title) })
                    }
                }
            }
            when {
                state.loading && state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ExpressiveLoadingIndicator()
                }
                state.error != null && state.items.isEmpty() -> CoolapkErrorState(
                    message = state.error ?: "加载失败",
                    onRetry = viewModel::retry,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.page > 0 && state.items.isEmpty() -> CoolapkEmptyState(
                    title = "暂无${state.kind.title}",
                    modifier = Modifier.fillMaxWidth(),
                )
                else -> LazyColumn(
                    modifier = Modifier.widthIn(max = 900.dp).fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(state.items, key = { _, item -> item.uid }) { _, user ->
                        UserRelationRow(user) { onOpenUser(user.uid) }
                    }
                    if (state.loading) item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            ExpressiveLoadingIndicator()
                        }
                    }
                    if (!state.loading && state.hasMore && state.items.isNotEmpty()) item(key = "more") {
                        TextButton(onClick = viewModel::loadMore, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("加载更多")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserRelationRow(user: UserSummary, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(user.username, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            user.bio?.takeIf(String::isNotBlank)?.let {
                Text(it.toPlainText(), style = MaterialTheme.typography.bodySmall, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
            }
        },
        leadingContent = { UserAvatar(user.avatarUrl, user.username, 48.dp) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.clip(MaterialTheme.shapes.large).clickable(onClick = onClick),
    )
}
