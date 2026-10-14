package app.coolapk.kmp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.coolapk.kmp.core.FeedItem
import com.coolapk.kmp.core.AppItem
import com.coolapk.kmp.core.FeedChannel
import com.coolapk.kmp.core.HomeCursor
import com.coolapk.kmp.core.HomePage
import com.coolapk.kmp.core.HomeRepository
import com.coolapk.kmp.core.NotificationSummary
import com.coolapk.kmp.core.UserProfile
import com.coolapk.kmp.core.createPlatformGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedPageState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val items: List<FeedItem> = emptyList(),
    val page: Int = 0,
    val cursor: HomeCursor? = null,
    val hasMore: Boolean = true,
    val error: String? = null,
)

data class HomeUiState(
    val channelPages: Map<FeedChannel, FeedPageState> = emptyMap(),
    val searchPage: FeedPageState = FeedPageState(),
    val likeOverrides: Map<String, Boolean> = emptyMap(),
    val likingIds: Set<String> = emptySet(),
    val interactionError: String? = null,
    val channel: FeedChannel = FeedChannel.HOME,
    val searchQuery: String? = null,
    val apps: List<AppItem> = emptyList(),
    val appsLoading: Boolean = false,
    val appPage: Int = 0,
    val appsHasMore: Boolean = true,
    val appCategory: String = "recommend",
    val appsError: String? = null,
    val profile: UserProfile? = null,
    val profileLoading: Boolean = false,
    val profileError: String? = null,
    val notifications: NotificationSummary = NotificationSummary(0),
) {
    val feedPage: FeedPageState get() = if (searchQuery != null) searchPage
        else channelPages[channel] ?: FeedPageState()
    val loading: Boolean get() = feedPage.loading
    val refreshing: Boolean get() = feedPage.refreshing
    val items: List<FeedItem> get() = feedPage.items
    val page: Int get() = feedPage.page
    val cursor: HomeCursor? get() = feedPage.cursor
    val hasMore: Boolean get() = feedPage.hasMore
    val error: String? get() = feedPage.error
}

class HomeViewModel(
    private val repositoryProvider: () -> HomeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val repository: HomeRepository? by lazy {
        runCatching(repositoryProvider).getOrNull()
    }
    private val channelRequestIds = mutableMapOf<FeedChannel, Int>()
    private var searchRequestId = 0
    private var appRequestId = 0
    private var profileRequestId = 0
    private var notificationRequestId = 0
    private var sessionGeneration = 0

    init {
        refresh()
    }

    fun refresh(force: Boolean = false) {
        val snapshot = _state.value
        if (snapshot.loading && !force) return
        val target = FeedTarget(snapshot.channel, snapshot.searchQuery)
        val requestId = nextFeedRequest(target)
        val generation = sessionGeneration
        updateFeedPage(target) { it.copy(loading = true, refreshing = true, error = null) }
        viewModelScope.launch {
            val repository = repository
            if (repository == null) {
                if (!isCurrentFeedRequest(target, requestId, generation)) return@launch
                updateFeedPage(target) {
                    it.copy(loading = false, refreshing = false,
                        error = "Rust 核心尚未就绪，请先构建 Android 原生库")
                }
                return@launch
            }
            try {
                val page = if (target.query != null) repository.search(target.query, page = 1)
                    else repository.load(channel = target.channel, page = 1)
                if (!isCurrentFeedRequest(target, requestId, generation)) return@launch
                updateFeedPage(target) {
                    it.copy(loading = false, refreshing = false,
                        items = page.items.distinctBy(FeedItem::id), page = 1,
                        cursor = page.cursor(), hasMore = page.hasMore, error = null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (!isCurrentFeedRequest(target, requestId, generation)) return@launch
                updateFeedPage(target) {
                    it.copy(loading = false, refreshing = false,
                        error = failure.message ?: "加载失败")
                }
            }
        }
    }

    fun selectChannel(channel: FeedChannel) {
        val snapshot = _state.value
        if (snapshot.channel == channel && snapshot.searchQuery != null && channel == FeedChannel.HOME) return
        if (snapshot.channel == channel && snapshot.searchQuery == null) {
            if (snapshot.page == 0 && !snapshot.loading) refresh()
            return
        }
        ++searchRequestId
        _state.update { it.copy(channel = channel, searchQuery = null, searchPage = FeedPageState()) }
        val restored = _state.value.feedPage
        if (restored.page == 0 && !restored.loading && restored.error == null) refresh()
    }

    fun clearSearch() {
        if (_state.value.searchQuery == null) return
        ++searchRequestId
        _state.update { it.copy(searchQuery = null, searchPage = FeedPageState()) }
        if (_state.value.page == 0 && !_state.value.loading) refresh()
    }

    fun loadApps(category: String = _state.value.appCategory) {
        if (_state.value.appsLoading && _state.value.appCategory == category) return
        val requestId = ++appRequestId
        _state.update {
            val sameCategory = it.appCategory == category
            it.copy(
                appsLoading = true,
                apps = if (sameCategory) it.apps else emptyList(),
                appCategory = category,
                appPage = if (sameCategory) it.appPage else 0,
                appsHasMore = if (sameCategory) it.appsHasMore else true,
                appsError = null,
            )
        }
        viewModelScope.launch {
            val repository = repository
            if (repository == null) {
                if (requestId == appRequestId) {
                    _state.update { it.copy(appsLoading = false, appsError = "数据服务尚未就绪") }
                }
                return@launch
            }
            runCatching { repository.loadApps(category = category) }
                .onSuccess { page ->
                    if (requestId != appRequestId) return@onSuccess
                    _state.update {
                        it.copy(appsLoading = false, apps = page.items, appPage = 1,
                            appsHasMore = page.hasMore, appsError = null)
                    }
                }
                .onFailure { error ->
                    if (requestId != appRequestId) return@onFailure
                    _state.update { it.copy(appsLoading = false, appsError = error.message ?: "应用加载失败") }
                }
        }
    }

    fun loadMoreApps() {
        val snapshot = _state.value
        if (snapshot.appsLoading || !snapshot.appsHasMore || snapshot.appPage == 0) return
        val requestId = ++appRequestId
        _state.update { it.copy(appsLoading = true, appsError = null) }
        viewModelScope.launch {
            val repository = repository
            if (repository == null) {
                if (requestId == appRequestId) {
                    _state.update { it.copy(appsLoading = false, appsError = "数据服务尚未就绪") }
                }
                return@launch
            }
            runCatching { repository.loadApps(page = snapshot.appPage + 1, category = snapshot.appCategory) }
                .onSuccess { page ->
                    if (requestId != appRequestId) return@onSuccess
                    _state.update {
                        it.copy(
                            appsLoading = false,
                            apps = (it.apps + page.items).distinctBy(AppItem::id),
                            appPage = snapshot.appPage + 1,
                            appsHasMore = page.hasMore && page.items.any { item ->
                                item.id !in snapshot.apps.map(AppItem::id)
                            },
                        )
                    }
                }
                .onFailure { error ->
                    if (requestId != appRequestId) return@onFailure
                    _state.update { it.copy(appsLoading = false, appsError = error.message ?: "应用加载失败") }
                }
        }
    }

    fun loadProfile(force: Boolean = false) {
        if (_state.value.profile != null && !force) return
        if (_state.value.profileLoading && !force) return
        val requestId = ++profileRequestId
        _state.update { it.copy(profileLoading = true, profileError = null) }
        viewModelScope.launch {
            runCatching { repository?.loadProfile() }
                .onSuccess { profile ->
                    if (requestId == profileRequestId) {
                        _state.update { it.copy(profileLoading = false, profile = profile, profileError = null) }
                    }
                }
                .onFailure { error ->
                    if (requestId == profileRequestId) {
                        _state.update { it.copy(profileLoading = false, profileError = error.message ?: "个人资料加载失败") }
                    }
                }
        }
    }

    fun refreshProfile() = loadProfile(force = true)

    fun loadNotifications() {
        val requestId = ++notificationRequestId
        viewModelScope.launch {
            runCatching { repository?.loadNotifications() }
                .onSuccess { summary ->
                    if (requestId == notificationRequestId && summary != null) {
                        _state.update { it.copy(notifications = summary) }
                    }
                }
        }
    }

    fun loadMore() {
        val snapshot = _state.value
        if (snapshot.loading || snapshot.items.isEmpty() || !snapshot.hasMore) return
        val target = FeedTarget(snapshot.channel, snapshot.searchQuery)
        val requestId = nextFeedRequest(target)
        val generation = sessionGeneration
        val previous = snapshot.feedPage
        updateFeedPage(target) { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val repository = repository
            if (repository == null) {
                if (isCurrentFeedRequest(target, requestId, generation)) {
                    updateFeedPage(target) { it.copy(loading = false, error = "数据服务尚未就绪") }
                }
                return@launch
            }
            val nextPage = previous.page + 1
            try {
                val page = if (target.query != null) repository.search(target.query, nextPage)
                    else repository.load(target.channel, nextPage, previous.cursor)
                if (!isCurrentFeedRequest(target, requestId, generation)) return@launch
                val seenIds = previous.items.map(FeedItem::id).toSet()
                val fresh = page.items.filterNot { it.id in seenIds }.distinctBy(FeedItem::id)
                updateFeedPage(target) { current ->
                    current.copy(loading = false, items = current.items + fresh,
                        page = nextPage, cursor = page.cursor() ?: previous.cursor,
                        hasMore = page.hasMore && fresh.isNotEmpty(), error = null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (!isCurrentFeedRequest(target, requestId, generation)) return@launch
                updateFeedPage(target) { it.copy(loading = false, error = failure.message ?: "加载失败") }
            }
        }
    }

    fun toggleLike(item: FeedItem) {
        if (item.id in _state.value.likingIds) return
        val previousOverride = _state.value.likeOverrides[item.id]
        val liked = previousOverride ?: item.isLiked
        val generation = sessionGeneration
        _state.update { state ->
            state.copy(
                likeOverrides = state.likeOverrides + (item.id to !liked),
                likingIds = state.likingIds + item.id,
                interactionError = null,
            )
        }
        viewModelScope.launch {
            val result = repository?.likeFeed(item.id, !liked)
            if (generation != sessionGeneration) return@launch
            if (result == null || result.isFailure) {
                _state.update { state ->
                    state.copy(
                        likeOverrides = if (previousOverride == null) state.likeOverrides - item.id
                            else state.likeOverrides + (item.id to previousOverride),
                        likingIds = state.likingIds - item.id,
                        interactionError = result?.exceptionOrNull()?.message ?: "数据服务尚未就绪",
                    )
                }
            } else {
                _state.update { it.copy(likingIds = it.likingIds - item.id) }
            }
        }
    }

    fun reply(item: FeedItem, message: String, onResult: (String?) -> Unit = {}) {
        val generation = sessionGeneration
        viewModelScope.launch {
            val result = repository?.replyFeed(item.id, message)
            onResult(when {
                generation != sessionGeneration -> "登录状态已变化，请重试"
                result == null -> "数据服务尚未就绪"
                else -> result.exceptionOrNull()?.message
            })
        }
    }

    fun clearInteractionError() {
        _state.update { it.copy(interactionError = null) }
    }

    fun loginWithCookie(cookie: String, onResult: (String?) -> Unit = {}) {
        loginWithAuthorization(cookie, null, onResult)
    }

    fun loginWithAuthorization(
        cookie: String,
        accessCode: String?,
        onResult: (String?) -> Unit = {},
    ) {
        viewModelScope.launch {
            runCatching {
                repository?.let { source ->
                    if (accessCode.isNullOrBlank()) source.loginWithCookie(cookie)
                    else runCatching { source.loginWithAccessCode(accessCode, cookie) }
                        .getOrElse { source.loginWithCookie(cookie) }
                }
            }
                .onSuccess { session ->
                    if (session == null) {
                        onResult("数据服务尚未就绪")
                    } else {
                        resetAccountData()
                        loadProfile()
                        loadNotifications()
                        onResult(null)
                    }
                }
                .onFailure { error -> onResult(error.message ?: "登录失败") }
        }
    }

    fun logout() {
        runCatching { repository?.clearSession() }
        resetAccountData()
    }

    private fun resetAccountData() {
        ++sessionGeneration
        channelRequestIds.clear()
        ++searchRequestId
        ++appRequestId
        ++profileRequestId
        ++notificationRequestId
        _state.update {
            it.copy(
                channelPages = emptyMap(),
                searchPage = FeedPageState(),
                likeOverrides = emptyMap(),
                likingIds = emptySet(),
                interactionError = null,
                channel = FeedChannel.HOME,
                searchQuery = null,
                apps = emptyList(),
                appsLoading = false,
                appPage = 0,
                appsHasMore = true,
                appsError = null,
                profile = null,
                profileLoading = false,
                profileError = null,
                notifications = NotificationSummary(0),
            )
        }
    }

    fun searchNow(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        _state.update {
            it.copy(channel = FeedChannel.HOME, searchQuery = trimmed, searchPage = FeedPageState())
        }
        refresh(force = true)
    }

    private data class FeedTarget(val channel: FeedChannel, val query: String?)

    private fun nextFeedRequest(target: FeedTarget): Int {
        if (target.query != null) return ++searchRequestId
        val next = (channelRequestIds[target.channel] ?: 0) + 1
        channelRequestIds[target.channel] = next
        return next
    }

    private fun isCurrentFeedRequest(target: FeedTarget, requestId: Int, generation: Int): Boolean =
        generation == sessionGeneration && if (target.query != null) {
            searchRequestId == requestId && _state.value.searchQuery == target.query
        } else {
            channelRequestIds[target.channel] == requestId
        }

    private fun updateFeedPage(target: FeedTarget, transform: (FeedPageState) -> FeedPageState) {
        _state.update { state ->
            if (target.query != null) {
                if (state.searchQuery == target.query) state.copy(searchPage = transform(state.searchPage))
                else state
            } else {
                state.copy(channelPages = state.channelPages +
                    (target.channel to transform(state.channelPages[target.channel] ?: FeedPageState())))
            }
        }
    }

    private fun HomePage.cursor(): HomeCursor? =
        firstItem?.let { first -> lastItem?.let { last -> HomeCursor(first, last) } }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                HomeViewModel {
                    HomeRepository(createPlatformGateway())
                }
            }
        }
    }
}
