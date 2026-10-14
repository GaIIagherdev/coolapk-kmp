package app.coolapk.kmp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.coolapk.kmp.core.HomeRepository
import com.coolapk.kmp.core.NotificationCategory
import com.coolapk.kmp.core.NotificationItem
import com.coolapk.kmp.core.createPlatformGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationUiState(
    val loggedIn: Boolean? = null,
    val category: NotificationCategory = NotificationCategory.COMMENT,
    val items: List<NotificationItem> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val page: Int = 0,
    val hasMore: Boolean = true,
    val error: String? = null,
)

class NotificationViewModel(
    private val repositoryProvider: () -> HomeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(NotificationUiState())
    val state: StateFlow<NotificationUiState> = _state.asStateFlow()

    private val repository: HomeRepository? by lazy {
        runCatching(repositoryProvider).getOrNull()
    }
    private var generation = 0
    private var requestId = 0

    fun open() {
        val currentCategory = _state.value.category
        val currentGeneration = ++generation
        ++requestId
        _state.value = NotificationUiState(category = currentCategory, loading = true)
        viewModelScope.launch {
            runCatching {
                repository?.loadSessionState() ?: error("数据服务尚未就绪")
            }.onSuccess { session ->
                if (currentGeneration != generation) return@onSuccess
                if (session.loggedIn) {
                    _state.update { it.copy(loggedIn = true, loading = false) }
                    loadPage(reset = true)
                } else {
                    _state.update {
                        it.copy(loggedIn = false, loading = false, hasMore = false, error = null)
                    }
                }
            }.onFailure { failure ->
                if (currentGeneration != generation) return@onFailure
                _state.update {
                    it.copy(loading = false, error = failure.message ?: "登录状态读取失败")
                }
            }
        }
    }

    fun selectCategory(category: NotificationCategory) {
        if (_state.value.category == category) return
        ++requestId
        _state.update {
            it.copy(category = category, items = emptyList(), page = 0, hasMore = true,
                loading = false, refreshing = false, error = null)
        }
        if (_state.value.loggedIn == true) loadPage(reset = true)
    }

    fun refresh() {
        if (_state.value.loggedIn != true) open() else loadPage(reset = true)
    }

    fun loadMore() {
        val snapshot = _state.value
        if (snapshot.loggedIn != true || snapshot.loading || !snapshot.hasMore || snapshot.page == 0) return
        loadPage(reset = false)
    }

    fun retry() {
        val snapshot = _state.value
        if (snapshot.loggedIn != true) open() else loadPage(reset = snapshot.page == 0)
    }

    private fun loadPage(reset: Boolean) {
        val snapshot = _state.value
        if (snapshot.loggedIn != true || !reset && snapshot.loading) return
        val currentGeneration = generation
        val currentRequest = ++requestId
        val nextPage = if (reset) 1 else snapshot.page + 1
        _state.update {
            it.copy(
                loading = true,
                refreshing = reset && it.items.isNotEmpty(),
                error = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                repository?.loadNotificationPage(snapshot.category, nextPage)
                    ?: error("数据服务尚未就绪")
            }.onSuccess { page ->
                if (currentGeneration != generation || currentRequest != requestId) return@onSuccess
                _state.update { state ->
                    val existingIds = if (reset) emptySet() else state.items.map(NotificationItem::id).toSet()
                    val newItems = page.items.filter { it.id !in existingIds }
                    state.copy(
                        items = if (reset) page.items.distinctBy(NotificationItem::id)
                            else state.items + newItems.distinctBy(NotificationItem::id),
                        page = nextPage,
                        hasMore = page.hasMore && newItems.isNotEmpty(),
                        loading = false,
                        refreshing = false,
                        error = null,
                    )
                }
            }.onFailure { failure ->
                if (currentGeneration != generation || currentRequest != requestId) return@onFailure
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = failure.message ?: "通知加载失败",
                    )
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                NotificationViewModel {
                    HomeRepository(createPlatformGateway())
                }
            }
        }
    }
}
