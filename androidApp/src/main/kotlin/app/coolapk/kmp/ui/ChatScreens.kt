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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.coolapk.kmp.core.ChatMessage
import com.coolapk.kmp.core.ChatSession
import com.coolapk.kmp.core.HomeRepository
import com.coolapk.kmp.core.createPlatformGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class ChatListState(
    val sessions: List<ChatSession> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

internal class MessagesViewModel(
    private val repositoryProvider: () -> HomeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatListState(loading = true))
    val state: StateFlow<ChatListState> = _state.asStateFlow()
    private val repository by lazy { runCatching(repositoryProvider).getOrNull() }

    init {
        load()
    }

    fun load() {
        val source = repository ?: run {
            _state.update { it.copy(loading = false, error = "数据服务尚未就绪") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val sessions = source.loadRecentChats(1)
                _state.update { it.copy(sessions = sessions, loading = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _state.update { it.copy(loading = false, error = failure.message ?: "私信加载失败") }
            }
        }
    }

    fun delete(ukey: String) {
        val source = repository ?: return
        viewModelScope.launch {
            source.deleteChat(ukey)
                .onSuccess { _state.update { it.copy(sessions = it.sessions.filterNot { s -> s.ukey == ukey }) } }
                .onFailure { failure -> _state.update { it.copy(error = failure.message ?: "删除失败") } }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { MessagesViewModel { HomeRepository(createPlatformGateway()) } }
        }
    }
}

internal data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val sending: Boolean = false,
    val sendError: String? = null,
    val hasMore: Boolean = true,
    val page: Int = 0,
    val firstItem: String? = null,
    val lastItem: String? = null,
)

internal class ChatViewModel(
    private val ukey: String,
    private val uid: String,
    private val repositoryProvider: () -> HomeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState(loading = true))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()
    private val repository by lazy { runCatching(repositoryProvider).getOrNull() }

    init {
        loadPage(page = 1, firstItem = null, lastItem = null)
    }

    fun loadMore() {
        val snapshot = _state.value
        if (snapshot.loading || !snapshot.hasMore || snapshot.page == 0) return
        loadPage(snapshot.page + 1, snapshot.firstItem, snapshot.lastItem)
    }

    private fun loadPage(page: Int, firstItem: String?, lastItem: String?) {
        val source = repository ?: run {
            _state.update { it.copy(loading = false, error = "数据服务尚未就绪") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val result = source.loadChatHistory(ukey, page, firstItem, lastItem)
                _state.update { state ->
                    val existing = state.messages.map { it.id }.toSet()
                    val fresh = result.items.filterNot { it.id in existing }
                    state.copy(
                        messages = if (page == 1) result.items else fresh + state.messages,
                        loading = false,
                        page = page,
                        firstItem = result.firstItem ?: state.firstItem,
                        lastItem = result.lastItem ?: state.lastItem,
                        hasMore = result.hasMore && fresh.isNotEmpty(),
                    )
                }
                source.markChatRead(ukey)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _state.update { it.copy(loading = false, error = failure.message ?: "聊天记录加载失败") }
            }
        }
    }

    fun send(text: String, onSent: () -> Unit) {
        val source = repository ?: return
        if (_state.value.sending || text.isBlank()) return
        _state.update { it.copy(sending = true, sendError = null) }
        viewModelScope.launch {
            source.sendPrivateMessage(uid, text.trim())
                .onSuccess {
                    _state.update {
                        it.copy(
                            sending = false,
                            messages = it.messages + ChatMessage(
                                id = "local-${System.currentTimeMillis()}",
                                uid = null,
                                username = "我",
                                avatarUrl = null,
                                text = text.trim(),
                                time = null,
                            ),
                        )
                    }
                    onSent()
                }
                .onFailure { failure -> _state.update { it.copy(sending = false, sendError = failure.message ?: "发送失败") } }
        }
    }

    companion object {
        fun factory(ukey: String, uid: String) = viewModelFactory {
            initializer { ChatViewModel(ukey, uid) { HomeRepository(createPlatformGateway()) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MessagesScreen(
    onBack: () -> Unit,
    onOpenChat: (ChatSession) -> Unit,
    viewModel: MessagesViewModel = viewModel(factory = MessagesViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("私信") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                state.loading && state.sessions.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ExpressiveLoadingIndicator()
                }
                state.error != null && state.sessions.isEmpty() -> CoolapkErrorState(
                    message = state.error ?: "加载失败", onRetry = viewModel::load, modifier = Modifier.fillMaxWidth(),
                )
                state.sessions.isEmpty() -> CoolapkEmptyState(title = "还没有私信", modifier = Modifier.fillMaxWidth())
                else -> LazyColumn(
                    modifier = Modifier.widthIn(max = 900.dp).fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(state.sessions, key = { _, s -> s.ukey }) { _, session ->
                        SessionRow(session, onClick = { onOpenChat(session) }, onDelete = { viewModel.delete(session.ukey) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionRow(session: ChatSession, onClick: () -> Unit, onDelete: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(session.username, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(session.lastMessage, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        },
        leadingContent = { UserAvatar(session.avatarUrl, session.username, 48.dp) },
        overlineContent = { session.time?.let { Text(it, style = MaterialTheme.typography.labelSmall) } },
        trailingContent = {
            if (session.unread > 0) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.error) {
                    Text(
                        if (session.unread > 99) "99+" else session.unread.toString(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onError,
                    )
                }
            } else {
                TextButton(onClick = onDelete) { Text("删除") }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatScreen(
    ukey: String,
    uid: String,
    title: String,
    selfUid: String?,
    onBack: () -> Unit,
    viewModel: ChatViewModel = viewModel(key = "chat-$ukey", factory = ChatViewModel.factory(ukey, uid)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
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
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(500) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("发送私信") },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4,
                )
                Spacer(Modifier.size(8.dp))
                FilledIconButton(
                    onClick = { viewModel.send(draft) { draft = "" } },
                    enabled = draft.isNotBlank() && !state.sending,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "发送")
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading && state.messages.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ExpressiveLoadingIndicator()
                }
                state.error != null && state.messages.isEmpty() -> CoolapkErrorState(
                    message = state.error ?: "加载失败",
                    onRetry = viewModel::loadMore,
                    modifier = Modifier.fillMaxWidth(),
                )
                else -> LazyColumn(
                    modifier = Modifier.widthIn(max = 900.dp).fillMaxSize().align(Alignment.CenterHorizontally),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.hasMore && state.messages.isNotEmpty()) item(key = "more") {
                        TextButton(onClick = viewModel::loadMore, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("加载更多")
                        }
                    }
                    itemsIndexed(state.messages, key = { _, m -> m.id }) { _, message ->
                        MessageBubble(message, mine = selfUid != null && message.uid == selfUid || message.uid == null)
                    }
                    state.sendError?.let { error -> item(key = "send-error") {
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    } }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, mine: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 20.dp, topEnd = 20.dp,
                bottomStart = if (mine) 20.dp else 6.dp,
                bottomEnd = if (mine) 6.dp else 20.dp,
            ),
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(
                message.text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (mine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
