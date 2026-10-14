package app.coolapk.kmp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.coolapk.kmp.core.FeedItem
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
