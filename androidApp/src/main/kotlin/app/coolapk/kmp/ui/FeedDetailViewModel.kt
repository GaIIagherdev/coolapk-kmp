package app.coolapk.kmp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.coolapk.kmp.core.CaptchaChallenge
import com.coolapk.kmp.core.FeedDetailFallback
import com.coolapk.kmp.core.FeedItem
import com.coolapk.kmp.core.HomeRepository
import com.coolapk.kmp.core.ReplyItem
import com.coolapk.kmp.core.ReplySort
import com.coolapk.kmp.core.createPlatformGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedDetailUiState(
    val feedId: String = "",
    val detail: FeedItem? = null,
    val detailLoading: Boolean = false,
    val detailError: String? = null,
    /** 非空时界面需要弹出人机验证，完成后回传 validate。 */
    val captcha: CaptchaChallenge? = null,
    val replies: List<ReplyItem> = emptyList(),
    val repliesLoading: Boolean = false,
    val repliesError: String? = null,
    val repliesPage: Int = 0,
    val repliesHasMore: Boolean = true,
    val sort: ReplySort = ReplySort.LATEST,
    val authorOnly: Boolean = false,
    val threadParent: ReplyItem? = null,
    val threadReplies: List<ReplyItem> = emptyList(),
    val threadLoading: Boolean = false,
    val threadError: String? = null,
    val threadPage: Int = 0,
    val threadHasMore: Boolean = true,
    val feedLikeOverride: Boolean? = null,
    val feedLiking: Boolean = false,
    val replyLikeOverrides: Map<String, Boolean> = emptyMap(),
    val likingReplyIds: Set<String> = emptySet(),
    val interactionError: String? = null,
)

class FeedDetailViewModel(
    private val repositoryProvider: () -> HomeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(FeedDetailUiState())
    val state: StateFlow<FeedDetailUiState> = _state.asStateFlow()

    private val repository: HomeRepository? by lazy {
        runCatching(repositoryProvider).getOrNull()
    }
    private var feedGeneration = 0
    private var detailRequest = 0
    private var replyRequest = 0
    private var threadRequest = 0

    fun open(feedId: String, preview: FeedItem?) {
        ++feedGeneration
        ++detailRequest
        ++replyRequest
        ++threadRequest
        _state.value = FeedDetailUiState(feedId = feedId, detail = preview)
        refresh()
    }

    fun refresh() {
        loadDetail()
        loadReplies(reset = true)
    }

    fun loadDetail() {
        val feedId = _state.value.feedId.takeIf(String::isNotBlank) ?: return
        val generation = feedGeneration
        val request = ++detailRequest
        _state.update { it.copy(detailLoading = true, detailError = null, captcha = null) }
        viewModelScope.launch {
            runCatching { repository?.loadFeedDetail(feedId) ?: error("数据服务尚未就绪") }
                .onSuccess { detail ->
                    if (generation != feedGeneration || request != detailRequest) return@onSuccess
                    _state.update { it.copy(detail = detail, detailLoading = false, detailError = null) }
                }
                .onFailure { error ->
                    if (generation != feedGeneration || request != detailRequest) return@onFailure
                    val challenge = FeedDetailFallback.captchaChallengeFrom(error)
                    if (challenge != null) {
                        // 交给界面弹出人机验证；取消或失败时由 dismissCaptcha 走网页兜底。
                        _state.update { it.copy(detailLoading = false, captcha = challenge, detailError = null) }
                    } else {
                        _state.update { it.copy(detailLoading = false, detailError = error.displayMessage()) }
                    }
                }
        }
    }

    /** 用户完成易盾滑块后回传 validate；带令牌重试一次，仍失败则退回网页版入口。 */
    fun submitCaptcha(validate: String) {
        val challenge = _state.value.captcha ?: return
        val feedId = _state.value.feedId.takeIf(String::isNotBlank) ?: return
        val token = FeedDetailFallback.buildPostToken(challenge.captchaId, validate)
        val generation = feedGeneration
        val request = ++detailRequest
        _state.update { it.copy(captcha = null, detailLoading = true, detailError = null) }
        viewModelScope.launch {
            runCatching {
                repository?.loadFeedDetailWithToken(feedId, token, challenge.captchaField)
                    ?: error("数据服务尚未就绪")
            }
                .onSuccess { detail ->
                    if (generation != feedGeneration || request != detailRequest) return@onSuccess
                    _state.update { it.copy(detail = detail, detailLoading = false, detailError = null) }
                }
                .onFailure {
                    if (generation != feedGeneration || request != detailRequest) return@onFailure
                    loadWebFallback(feedId, generation)
                }
        }
    }

    /** 用户取消人机验证，或验证流程不可用时，直接改用网页版入口。 */
    fun dismissCaptcha() {
        if (_state.value.captcha == null) return
        val feedId = _state.value.feedId.takeIf(String::isNotBlank) ?: return
        _state.update { it.copy(captcha = null) }
        loadWebFallback(feedId, feedGeneration)
    }

    /** 详情接口与令牌重试都不可用时的只读兜底：酷安网页版入口在 XHR 头下直接返回 JSON。 */
    private fun loadWebFallback(feedId: String, generation: Int) {
        val request = ++detailRequest
        _state.update { it.copy(detailLoading = true, detailError = null) }
        viewModelScope.launch {
            runCatching { repository?.loadWebFeedDetail(feedId) ?: error("数据服务尚未就绪") }
                .onSuccess { detail ->
                    if (generation != feedGeneration || request != detailRequest) return@onSuccess
                    _state.update { it.copy(detail = detail, detailLoading = false, detailError = null) }
                }
                .onFailure { error ->
                    if (generation != feedGeneration || request != detailRequest) return@onFailure
                    _state.update {
                        it.copy(
                            detailLoading = false,
                            detailError = "网页版动态也未能加载：${error.displayMessage()}",
                        )
                    }
                }
        }
    }

    fun selectSort(sort: ReplySort) {
        if (_state.value.sort == sort && !_state.value.authorOnly) return
        _state.update { it.copy(sort = sort, authorOnly = false) }
        loadReplies(reset = true)
    }

    fun setAuthorOnly(authorOnly: Boolean) {
        if (_state.value.authorOnly == authorOnly) return
        _state.update { it.copy(authorOnly = authorOnly) }
        loadReplies(reset = true)
    }

    fun loadMoreReplies() = loadReplies(reset = false)

    private fun loadReplies(reset: Boolean) {
        val snapshot = _state.value
        val feedId = snapshot.feedId.takeIf(String::isNotBlank) ?: return
        if (!reset && (snapshot.repliesLoading || !snapshot.repliesHasMore || snapshot.repliesPage == 0)) return
        val generation = feedGeneration
        val request = ++replyRequest
        val nextPage = if (reset) 1 else snapshot.repliesPage + 1
        _state.update {
            it.copy(
                replies = if (reset) emptyList() else it.replies,
                repliesPage = if (reset) 0 else it.repliesPage,
                repliesHasMore = if (reset) true else it.repliesHasMore,
                repliesLoading = true,
                repliesError = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                repository?.loadFeedReplies(feedId, nextPage, snapshot.sort, snapshot.authorOnly)
                    ?: error("数据服务尚未就绪")
            }
                .onSuccess { page ->
                    if (generation != feedGeneration || request != replyRequest) return@onSuccess
                    _state.update { state ->
                        val previousIds = state.replies.map(ReplyItem::id).toSet()
                        state.copy(
                            replies = (state.replies + page.items).distinctBy(ReplyItem::id),
                            repliesPage = nextPage,
                            repliesHasMore = page.hasMore && page.items.any { it.id !in previousIds },
                            repliesLoading = false,
                        )
                    }
                }
                .onFailure { error ->
                    if (generation != feedGeneration || request != replyRequest) return@onFailure
                    _state.update { it.copy(repliesLoading = false, repliesError = error.message ?: "评论加载失败") }
                }
        }
    }

    fun openThread(parent: ReplyItem) {
        ++threadRequest
        _state.update {
            it.copy(threadParent = parent, threadReplies = emptyList(), threadPage = 0,
                threadHasMore = true, threadError = null)
        }
        loadThread(reset = true)
    }

    fun closeThread() {
        ++threadRequest
        _state.update {
            it.copy(threadParent = null, threadReplies = emptyList(), threadPage = 0,
                threadLoading = false, threadError = null)
        }
    }

    fun loadMoreThread() = loadThread(reset = false)

    fun retryThread() = loadThread(reset = _state.value.threadPage == 0)

    private fun loadThread(reset: Boolean) {
        val snapshot = _state.value
        val parent = snapshot.threadParent ?: return
        if (!reset && (snapshot.threadLoading || !snapshot.threadHasMore || snapshot.threadPage == 0)) return
        val generation = feedGeneration
        val request = ++threadRequest
        val nextPage = if (reset) 1 else snapshot.threadPage + 1
        val lastItem = if (reset) null else snapshot.threadReplies.lastOrNull()?.id
        _state.update {
            it.copy(
                threadReplies = if (reset) emptyList() else it.threadReplies,
                threadPage = if (reset) 0 else it.threadPage,
                threadHasMore = if (reset) true else it.threadHasMore,
                threadLoading = true,
                threadError = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                repository?.loadSubReplies(snapshot.feedId, parent.id, nextPage, lastItem)
                    ?: error("数据服务尚未就绪")
            }
                .onSuccess { page ->
                    if (generation != feedGeneration || request != threadRequest) return@onSuccess
                    _state.update { state ->
                        val previousIds = state.threadReplies.map(ReplyItem::id).toSet()
                        state.copy(
                            threadReplies = (state.threadReplies + page.items).distinctBy(ReplyItem::id),
                            threadPage = nextPage,
                            threadHasMore = page.hasMore && page.items.any { it.id !in previousIds },
                            threadLoading = false,
                        )
                    }
                }
                .onFailure { error ->
                    if (generation != feedGeneration || request != threadRequest) return@onFailure
                    _state.update { it.copy(threadLoading = false, threadError = error.message ?: "回复加载失败") }
                }
        }
    }

    fun toggleFeedLike(onChanged: () -> Unit) {
        val snapshot = _state.value
        val detail = snapshot.detail ?: return
        if (snapshot.feedLiking) return
        val liked = snapshot.feedLikeOverride ?: detail.isLiked
        val generation = feedGeneration
        _state.update { it.copy(feedLikeOverride = !liked, feedLiking = true, interactionError = null) }
        viewModelScope.launch {
            val result = repository?.likeFeed(detail.id, !liked)
            if (generation != feedGeneration) return@launch
            if (result == null || result.isFailure) {
                _state.update {
                    it.copy(feedLikeOverride = snapshot.feedLikeOverride, feedLiking = false,
                        interactionError = result?.exceptionOrNull()?.message ?: "数据服务尚未就绪")
                }
            } else {
                _state.update { it.copy(feedLiking = false) }
                onChanged()
            }
        }
    }

    fun toggleReplyLike(reply: ReplyItem) {
        val snapshot = _state.value
        if (reply.id in snapshot.likingReplyIds) return
        val previousOverride = snapshot.replyLikeOverrides[reply.id]
        val liked = previousOverride ?: reply.isLiked
        val generation = feedGeneration
        _state.update {
            it.copy(replyLikeOverrides = it.replyLikeOverrides + (reply.id to !liked),
                likingReplyIds = it.likingReplyIds + reply.id, interactionError = null)
        }
        viewModelScope.launch {
            val result = repository?.likeReply(reply.id, !liked)
            if (generation != feedGeneration) return@launch
            if (result == null || result.isFailure) {
                _state.update {
                    it.copy(
                        replyLikeOverrides = if (previousOverride == null) it.replyLikeOverrides - reply.id
                            else it.replyLikeOverrides + (reply.id to previousOverride),
                        likingReplyIds = it.likingReplyIds - reply.id,
                        interactionError = result?.exceptionOrNull()?.message ?: "数据服务尚未就绪",
                    )
                }
            } else {
                _state.update { it.copy(likingReplyIds = it.likingReplyIds - reply.id) }
            }
        }
    }

    fun sendReply(
        target: ReplyItem?,
        message: String,
        onChanged: () -> Unit,
        onResult: (String?) -> Unit,
    ) {
        val feedId = _state.value.feedId
        val generation = feedGeneration
        viewModelScope.launch {
            val result = if (target == null) repository?.replyFeed(feedId, message)
                else repository?.replyToComment(feedId, target.id, message)
            if (generation != feedGeneration) return@launch
            val error = result?.exceptionOrNull()?.message ?: if (result == null) "数据服务尚未就绪" else null
            if (error == null) {
                refresh()
                if (_state.value.threadParent != null) loadThread(reset = true)
                onChanged()
            }
            onResult(error)
        }
    }

    fun clearInteractionError() {
        _state.update { it.copy(interactionError = null) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { FeedDetailViewModel { HomeRepository(createPlatformGateway()) } }
        }
    }
}

private fun Throwable.displayMessage(): String =
    message?.takeIf(String::isNotBlank) ?: "动态加载失败"
