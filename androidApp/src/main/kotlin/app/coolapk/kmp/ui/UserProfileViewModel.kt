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
)

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
