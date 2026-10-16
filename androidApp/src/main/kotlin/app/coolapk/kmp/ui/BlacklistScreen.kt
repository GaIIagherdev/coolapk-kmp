package app.coolapk.kmp.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coolapk.kmp.core.UserSummary

/** 黑名单：被屏蔽的用户列表，可移出黑名单。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BlacklistScreen(
    onBack: () -> Unit,
    onOpenUser: (String) -> Unit,
    viewModel: BlacklistViewModel = viewModel(key = "blacklist", factory = BlacklistViewModel.factory()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    LaunchedEffect(state.message) {
        state.message?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("黑名单") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            when {
                state.loading && state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ExpressiveLoadingIndicator()
                }
                state.error != null && state.items.isEmpty() -> CoolapkErrorState(
                    message = state.error.orEmpty(),
                    onRetry = viewModel::retry,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.page > 0 && state.items.isEmpty() -> CoolapkEmptyState(
                    title = "黑名单是空的",
                    detail = "在用户主页屏蔽的酷友会显示在这里。",
                    modifier = Modifier.fillMaxWidth(),
                )
                else -> LazyColumn(
                    modifier = Modifier.widthIn(max = 900.dp).fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.items, key = { it.uid }) { user ->
                        BlockedUserRow(user, onOpenUser = onOpenUser, onUnblock = { viewModel.unblock(user) })
                    }
                    if (state.loading) item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            ExpressiveLoadingIndicator()
                        }
                    }
                    if (!state.loading && state.hasMore && state.items.isNotEmpty()) item(key = "more") {
                        Text(
                            "加载更多",
                            modifier = Modifier.fillMaxWidth().clickable(onClick = viewModel::loadMore).padding(16.dp),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockedUserRow(user: UserSummary, onOpenUser: (String) -> Unit, onUnblock: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(
                user.username,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            user.bio?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        leadingContent = { UserAvatar(url = user.avatarUrl, name = user.username, size = 48.dp) },
        trailingContent = {
            OutlinedButton(onClick = onUnblock, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("移出")
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onOpenUser(user.uid) },
    )
}
