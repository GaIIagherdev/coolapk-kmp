package app.coolapk.kmp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.coolapk.kmp.core.FeedItem
import com.coolapk.kmp.core.HomeCardRow
import com.coolapk.kmp.core.HomeCursor
import com.coolapk.kmp.core.HomeRepository
import com.coolapk.kmp.core.UserProfile
import com.coolapk.kmp.core.createPlatformGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 收藏与赞过列表共用的分页状态，与动态列表相互独立。 */
data class ProfileFeedList(
    val items: List<FeedItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val retryPage: Int? = null,
    val page: Int = 0,
    val hasMore: Boolean = true,
)

enum class ProfileListKind { FAVORITES, LIKED }

data class UserProfileUiState(
    val requestedUid: String? = null,
    val profile: UserProfile? = null,
    val profileLoading: Boolean = false,
    val refreshing: Boolean = false,
    val profileError: String? = null,
    val loginRequired: Boolean = false,
    val feeds: List<FeedItem> = emptyList(),
    val feedsLoading: Boolean = false,
    val feedsError: String? = null,
    val feedsRetryPage: Int? = null,
    val feedPage: Int = 0,
    val hasMoreFeeds: Boolean = true,
    val favorites: ProfileFeedList = ProfileFeedList(),
    val liked: ProfileFeedList = ProfileFeedList(),
    val actionError: String? = null,
    /** 主页卡片（关注的人、关注的板块等）。 */
    val homeRows: List<HomeCardRow> = emptyList(),
    /** 分类动态（回复、评分、酷图、二手），key 为接口的 feedType。 */
    val typed: Map<String, ProfileFeedList> = emptyMap(),
    /** 标签页实体列表（图文、问答、好物榜等），key 为接口的 tab。 */
    val entityTabs: Map<String, DigitalContentState> = emptyMap(),
    val qrImage: String? = null,
    val qrLoading: Boolean = false,
    val qrError: String? = null,
)

private fun UserProfileUiState.listFor(kind: ProfileListKind): ProfileFeedList = when (kind) {
    ProfileListKind.FAVORITES -> favorites
    ProfileListKind.LIKED -> liked
}

private fun UserProfileUiState.withList(kind: ProfileListKind, list: ProfileFeedList): UserProfileUiState = when (kind) {
    ProfileListKind.FAVORITES -> copy(favorites = list)
    ProfileListKind.LIKED -> copy(liked = list)
}

class UserProfileViewModel(
    private val repositoryProvider: () -> HomeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(UserProfileUiState())
    val state: StateFlow<UserProfileUiState> = _state.asStateFlow()

    private var repository: HomeRepository? = null
    private var requestId = 0

    fun open(uid: String?, ownProfile: UserProfile? = null) = loadProfile(uid, ownProfile, retainContent = false)

    fun refresh() {
        val snapshot = _state.value
        loadProfile(snapshot.requestedUid, null, retainContent = snapshot.profile != null)
    }

    private fun loadProfile(uid: String?, ownProfile: UserProfile?, retainContent: Boolean) {
        val requestedUid = uid?.trim()?.takeIf(String::isNotEmpty)
        val currentRequest = ++requestId
        repository = runCatching(repositoryProvider).getOrNull()
        val source = repository
        val previous = _state.value
        val retain = retainContent && previous.requestedUid == requestedUid && previous.profile != null
        _state.value = if (retain) {
            previous.copy(
                profileLoading = true,
                refreshing = true,
                profileError = null,
                feedsLoading = false,
                feedsError = null,
                feedsRetryPage = null,
                favorites = ProfileFeedList(),
                liked = ProfileFeedList(),
                typed = emptyMap(),
                entityTabs = emptyMap(),
            )
        } else {
            UserProfileUiState(
                requestedUid = requestedUid,
                profile = if (requestedUid == null) ownProfile else null,
                profileLoading = ownProfile == null || requestedUid != null,
            )
        }
        viewModelScope.launch {
            if (source == null) {
                if (currentRequest == requestId) {
                    _state.update {
                        it.copy(profileLoading = false, refreshing = false, profileError = "数据服务尚未就绪")
                    }
                }
                return@launch
            }
            try {
                val profile = if (requestedUid == null) ownProfile ?: source.loadProfile()
                    else source.loadUserProfile(requestedUid)
                if (currentRequest != requestId) return@launch
                if (profile == null) {
                    _state.update {
                        it.copy(
                            profile = null,
                            profileLoading = false,
                            refreshing = false,
                            profileError = if (requestedUid == null) null else "未找到酷友空间",
                            loginRequired = requestedUid == null,
                            feeds = emptyList(),
                            feedsLoading = false,
                            feedsError = null,
                            feedsRetryPage = null,
                            feedPage = 0,
                            hasMoreFeeds = true,
                        )
                    }
                    return@launch
                }
                _state.update {
                    val sameUser = it.profile?.uid == profile.uid
                    it.copy(
                        profile = profile,
                        profileLoading = false,
                        profileError = null,
                        loginRequired = false,
                        feeds = if (sameUser) it.feeds else emptyList(),
                        feedPage = if (sameUser) it.feedPage else 0,
                    )
                }
                loadHomeRows(source, profile.uid, currentRequest)
                loadFeeds(source, profile.uid, 1, currentRequest)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (currentRequest == requestId) {
                    _state.update {
                        it.copy(
                            profileLoading = false,
                            refreshing = false,
                            profileError = failure.message ?: "个人主页加载失败",
                            feedsLoading = false,
                        )
                    }
                }
            }
        }
    }

    fun loadMore() {
        val snapshot = _state.value
        val uid = snapshot.profile?.uid ?: return
        val source = repository ?: return
        if (snapshot.feedsLoading || snapshot.refreshing || snapshot.feedsRetryPage != null ||
            !snapshot.hasMoreFeeds || snapshot.feedPage == 0
        ) return
        val currentRequest = requestId
        viewModelScope.launch { loadFeeds(source, uid, snapshot.feedPage + 1, currentRequest) }
    }

    /** 打开收藏或赞过标签时加载第一页；已加载过的列表不会重复请求。 */
    fun openList(kind: ProfileListKind) {
        val snapshot = _state.value
        val uid = snapshot.profile?.uid ?: return
        val source = repository ?: return
        val list = snapshot.listFor(kind)
        if (list.loading || list.page > 0) return
        val currentRequest = requestId
        viewModelScope.launch { loadList(kind, source, uid, 1, currentRequest) }
    }

    fun loadMoreList(kind: ProfileListKind) {
        val snapshot = _state.value
        val uid = snapshot.profile?.uid ?: return
        val source = repository ?: return
        val list = snapshot.listFor(kind)
        if (list.loading || list.retryPage != null || !list.hasMore || list.page == 0) return
        val currentRequest = requestId
        viewModelScope.launch { loadList(kind, source, uid, list.page + 1, currentRequest) }
    }

    fun retryList(kind: ProfileListKind) {
        val snapshot = _state.value
        val uid = snapshot.profile?.uid ?: return
        val source = repository ?: return
        val list = snapshot.listFor(kind)
        if (list.loading) return
        val currentRequest = requestId
        viewModelScope.launch { loadList(kind, source, uid, list.retryPage ?: (list.page + 1), currentRequest) }
    }

    private suspend fun loadList(kind: ProfileListKind, source: HomeRepository, uid: String, page: Int, currentRequest: Int) {
        if (currentRequest != requestId) return
        updateList(kind) { it.copy(loading = true, error = null, retryPage = null) }
        try {
            val result = when (kind) {
                ProfileListKind.FAVORITES -> source.loadUserFavorites(uid, page)
                ProfileListKind.LIKED -> source.loadUserLiked(uid, page)
            }
            if (currentRequest != requestId) return
            updateList(kind) { list ->
                val existingIds = if (page == 1) emptySet() else list.items.map(FeedItem::id).toSet()
                val newItems = result.items.filterNot { it.id in existingIds }.distinctBy(FeedItem::id)
                list.copy(
                    items = if (page == 1) newItems else list.items + newItems,
                    loading = false,
                    error = null,
                    page = page,
                    hasMore = result.hasMore && newItems.isNotEmpty(),
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (currentRequest == requestId) {
                updateList(kind) {
                    it.copy(loading = false, error = failure.message ?: "加载失败", retryPage = page)
                }
            }
        }
    }

    private fun updateList(kind: ProfileListKind, transform: (ProfileFeedList) -> ProfileFeedList) {
        _state.update { it.withList(kind, transform(it.listFor(kind))) }
    }

    /** 关注或取消关注：先乐观更新，失败时回滚并提示。 */
    fun toggleFollow() {
        val snapshot = _state.value
        val profile = snapshot.profile ?: return
        val source = repository ?: return
        val target = !profile.isFollow
        _state.update { it.copy(profile = it.profile?.copy(isFollow = target), actionError = null) }
        viewModelScope.launch {
            source.followUser(profile.uid, target).onFailure { failure ->
                _state.update {
                    it.copy(
                        profile = it.profile?.copy(isFollow = !target),
                        actionError = failure.message ?: "操作失败",
                    )
                }
            }
        }
    }

    private suspend fun loadHomeRows(source: HomeRepository, uid: String, currentRequest: Int) {
        val rows = runCatching { source.loadUserHomeRows(uid) }.getOrDefault(emptyList())
        if (currentRequest != requestId) return
        _state.update { it.copy(homeRows = rows) }
    }

    /** 设置或取消特别关注（仅在已关注时可用）。 */
    fun toggleSpecialFollow() {
        val snapshot = _state.value
        val profile = snapshot.profile ?: return
        if (!profile.isFollow) return
        val source = repository ?: return
        val target = !profile.isSpecialFollow
        _state.update { it.copy(profile = it.profile?.copy(isSpecialFollow = target), actionError = null) }
        viewModelScope.launch {
            source.setSpecialFollow(profile.uid, target).onFailure { failure ->
                _state.update {
                    it.copy(
                        profile = it.profile?.copy(isSpecialFollow = !target),
                        actionError = failure.message ?: "操作失败",
                    )
                }
            }
        }
    }

    /** 加载个人二维码名片（仅本人主页使用）。 */
    fun loadQrImage() {
        val uid = _state.value.profile?.uid ?: return
        val source = repository ?: return
        if (_state.value.qrImage != null || _state.value.qrLoading) return
        _state.update { it.copy(qrLoading = true, qrError = null) }
        viewModelScope.launch {
            runCatching { source.loadUserQrImage(uid) }
                .onSuccess { image ->
                    _state.update {
                        it.copy(
                            qrLoading = false,
                            qrImage = image,
                            qrError = if (image == null) "接口未返回二维码图片" else null,
                        )
                    }
                }
                .onFailure { failure ->
                    _state.update { it.copy(qrLoading = false, qrError = failure.message ?: "二维码加载失败") }
                }
        }
    }

    fun openTyped(feedType: String) {
        val list = _state.value.typed[feedType]
        if (list != null && (list.loading || list.page > 0)) return
        loadTyped(feedType, page = 1)
    }

    fun loadMoreTyped(feedType: String) {
        val list = _state.value.typed[feedType] ?: return
        if (list.loading || list.page == 0 || !list.hasMore || list.retryPage != null) return
        loadTyped(feedType, page = list.page + 1)
    }

    fun retryTyped(feedType: String) {
        val list = _state.value.typed[feedType]
        if (list?.loading == true) return
        loadTyped(feedType, page = list?.retryPage ?: ((list?.page ?: 0) + 1).coerceAtLeast(1))
    }

    private fun loadTyped(feedType: String, page: Int) {
        val uid = _state.value.profile?.uid ?: return
        val source = repository ?: return
        val request = requestId
        _state.update { state ->
            val previous = state.typed[feedType] ?: ProfileFeedList()
            state.copy(typed = state.typed + (feedType to previous.copy(loading = true, error = null, retryPage = null)))
        }
        viewModelScope.launch {
            try {
                val result = source.loadUserFeedsTyped(uid, feedType, page)
                if (request != requestId) return@launch
                _state.update { state ->
                    val previous = state.typed[feedType] ?: ProfileFeedList()
                    val existing = if (page == 1) emptySet() else previous.items.map(FeedItem::id).toSet()
                    val fresh = result.items.filterNot { it.id in existing }.distinctBy(FeedItem::id)
                    state.copy(
                        typed = state.typed + (feedType to previous.copy(
                            items = if (page == 1) fresh else previous.items + fresh,
                            loading = false,
                            error = null,
                            page = page,
                            hasMore = result.hasMore && fresh.isNotEmpty(),
                        )),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (request == requestId) {
                    _state.update { state ->
                        val previous = state.typed[feedType] ?: ProfileFeedList()
                        state.copy(typed = state.typed + (feedType to previous.copy(
                            loading = false,
                            error = failure.message ?: "加载失败",
                            retryPage = page,
                        )))
                    }
                }
            }
        }
    }

    fun openEntityTab(tab: String) {
        val current = _state.value.entityTabs[tab]
        if (current != null && (current.loading || current.page > 0)) return
        loadEntityTab(tab, page = 1, cursor = null)
    }

    fun loadMoreEntityTab(tab: String) {
        val current = _state.value.entityTabs[tab] ?: return
        if (current.loading || current.page == 0 || !current.hasMore) return
        loadEntityTab(tab, page = current.page + 1, cursor = current.cursor)
    }

    fun retryEntityTab(tab: String) {
        val current = _state.value.entityTabs[tab]
        if (current?.loading == true) return
        if (current == null || current.page == 0) {
            loadEntityTab(tab, page = 1, cursor = null)
        } else {
            loadEntityTab(tab, page = current.page + 1, cursor = current.cursor)
        }
    }

    private fun loadEntityTab(tab: String, page: Int, cursor: HomeCursor?) {
        val uid = _state.value.profile?.uid ?: return
        val source = repository ?: return
        val request = requestId
        _state.update { state ->
            val previous = state.entityTabs[tab] ?: DigitalContentState()
            state.copy(entityTabs = state.entityTabs + (tab to previous.copy(loading = true, error = null)))
        }
        viewModelScope.launch {
            try {
                val result = source.loadUserTabPage(uid, tab, page, cursor?.firstItem, cursor?.lastItem)
                if (request != requestId) return@launch
                _state.update { state ->
                    val previous = state.entityTabs[tab] ?: DigitalContentState()
                    val existing = if (page == 1) emptySet() else previous.items.map { it.id }.toSet()
                    val fresh = result.items.filterNot { it.id in existing }.distinctBy { it.id }
                    val firstItem = result.firstItem
                    val lastItem = result.lastItem
                    state.copy(entityTabs = state.entityTabs + (tab to previous.copy(
                        items = if (page == 1) fresh else previous.items + fresh,
                        loading = false,
                        error = null,
                        page = page,
                        cursor = if (firstItem != null && lastItem != null) HomeCursor(firstItem, lastItem) else previous.cursor,
                        hasMore = result.hasMore && fresh.isNotEmpty(),
                    )))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (request == requestId) {
                    _state.update { state ->
                        val previous = state.entityTabs[tab] ?: DigitalContentState()
                        state.copy(entityTabs = state.entityTabs + (tab to previous.copy(
                            loading = false,
                            error = failure.message ?: "加载失败",
                        )))
                    }
                }
            }
        }
    }

    /** 发送私信：成功后调用 onDone，失败时在页面提示。 */
    fun sendMessage(text: String, onDone: () -> Unit) {
        val uid = _state.value.profile?.uid ?: return
        val source = repository ?: return
        viewModelScope.launch {
            source.sendPrivateMessage(uid, text)
                .onSuccess { onDone() }
                .onFailure { failure -> _state.update { it.copy(actionError = failure.message ?: "私信发送失败") } }
        }
    }

    /** 移除粉丝：成功后把该用户标记为非粉丝。 */
    fun removeFan() {
        val profile = _state.value.profile ?: return
        val source = repository ?: return
        viewModelScope.launch {
            source.removeFan(profile.uid)
                .onSuccess { _state.update { it.copy(profile = it.profile?.copy(isFans = false), actionError = null) } }
                .onFailure { failure -> _state.update { it.copy(actionError = failure.message ?: "操作失败") } }
        }
    }

    fun dismissActionError() {
        _state.update { it.copy(actionError = null) }
    }

    fun retryFeeds() {
        val snapshot = _state.value
        val uid = snapshot.profile?.uid ?: return
        val source = repository ?: return
        if (snapshot.feedsLoading) return
        val currentRequest = requestId
        viewModelScope.launch {
            loadFeeds(source, uid, snapshot.feedsRetryPage ?: snapshot.feedPage + 1, currentRequest)
        }
    }

    private suspend fun loadFeeds(source: HomeRepository, uid: String, page: Int, currentRequest: Int) {
        if (currentRequest != requestId) return
        _state.update { it.copy(feedsLoading = true, feedsError = null, feedsRetryPage = null) }
        try {
            val result = source.loadUserFeeds(uid, page)
            if (currentRequest != requestId) return
            _state.update { state ->
                val existingIds = if (page == 1) emptySet() else state.feeds.map(FeedItem::id).toSet()
                val newItems = result.items.filterNot { it.id in existingIds }.distinctBy(FeedItem::id)
                state.copy(
                    feeds = if (page == 1) newItems else state.feeds + newItems,
                    feedsLoading = false,
                    refreshing = false,
                    feedsError = null,
                    feedPage = page,
                    hasMoreFeeds = result.hasMore && newItems.isNotEmpty(),
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (currentRequest == requestId) {
                _state.update {
                    it.copy(
                        feedsLoading = false,
                        refreshing = false,
                        feedsError = failure.message ?: "动态加载失败",
                        feedsRetryPage = page,
                    )
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { UserProfileViewModel { HomeRepository(createPlatformGateway()) } }
        }
    }
}
