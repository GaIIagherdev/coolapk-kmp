package app.coolapk.kmp.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.coolapk.kmp.core.HomeRepository
import com.coolapk.kmp.core.SessionState
import com.coolapk.kmp.core.createPlatformGateway
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PublishUiState(
    val message: String = "",
    val preview: Boolean = false,
    val loadingDraft: Boolean = true,
    val savingDraft: Boolean = false,
    val submitting: Boolean = false,
    val loggedIn: Boolean = false,
    val accountKey: String = "guest",
    val publishedFeedId: String? = null,
    val error: String? = null,
    val notice: String? = null,
) {
    val characterCount: Int get() = message.length
    val canSubmit: Boolean get() = loggedIn && message.trim().isNotEmpty() && characterCount <= MAX_MESSAGE_LENGTH && !submitting
    val hasUnsavedContent: Boolean get() = message.isNotBlank()

    companion object {
        const val MAX_MESSAGE_LENGTH = 1000
    }
}

internal class PublishViewModel(
    private val repositoryProvider: () -> HomeRepository,
    private val draftStore: PublishDraftStore,
) : ViewModel() {
    private val _state = MutableStateFlow(PublishUiState())
    val state: StateFlow<PublishUiState> = _state.asStateFlow()

    private val repository: HomeRepository? by lazy { runCatching(repositoryProvider).getOrNull() }
    private var draftJob: Job? = null
    private var submitGeneration = 0

    init {
        refreshSessionAndDraft()
    }

    fun setMessage(message: String) {
        val clipped = message.take(PublishUiState.MAX_MESSAGE_LENGTH * 4)
        _state.update { it.copy(message = clipped, error = null, notice = null, publishedFeedId = null) }
        scheduleDraftSave()
    }

    fun togglePreview() {
        _state.update { it.copy(preview = !it.preview, error = null) }
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun refreshSessionAndDraft() {
        if (_state.value.submitting) return
        val generation = ++submitGeneration
        val previous = _state.value
        _state.update { it.copy(loadingDraft = true, error = null) }
        viewModelScope.launch {
            val session = runCatching { repository?.loadSessionState() }.getOrNull()
            if (generation != submitGeneration) return@launch
            val accountKey = session.accountKey()
            if (accountKey != previous.accountKey) {
                val pendingDraftJob = draftJob
                pendingDraftJob?.cancel()
                pendingDraftJob?.join()
                if (previous.message.isNotBlank()) {
                    runCatching { draftStore.save(previous.accountKey, previous.message) }
                }
            }
            val draft = runCatching { draftStore.load(accountKey) }.getOrElse { "" }
            if (generation != submitGeneration) return@launch
            _state.update {
                val restoreDraft = accountKey != it.accountKey || it.message.isBlank()
                it.copy(
                    message = if (restoreDraft) draft else it.message,
                    loadingDraft = false,
                    loggedIn = session?.loggedIn == true,
                    accountKey = accountKey,
                    notice = if (draft.isNotBlank() && restoreDraft) "已恢复草稿" else it.notice,
                )
            }
        }
    }

    fun saveDraftNow(onSaved: () -> Unit = {}) {
        val pendingDraftJob = draftJob
        pendingDraftJob?.cancel()
        val snapshot = _state.value
        _state.update { it.copy(savingDraft = true, error = null) }
        viewModelScope.launch {
            pendingDraftJob?.join()
            runCatching { draftStore.save(snapshot.accountKey, snapshot.message) }
                .onSuccess {
                    _state.update { it.copy(savingDraft = false, notice = "草稿已保存") }
                    onSaved()
                }
                .onFailure { error ->
                    _state.update { it.copy(savingDraft = false, error = error.message ?: "草稿保存失败") }
                }
        }
    }

    fun clearDraft() {
        val pendingDraftJob = draftJob
        pendingDraftJob?.cancel()
        val accountKey = _state.value.accountKey
        viewModelScope.launch {
            pendingDraftJob?.join()
            runCatching { draftStore.clear(accountKey) }
                .onSuccess { _state.update { it.copy(message = "", notice = "草稿已清除", error = null) } }
                .onFailure { error -> _state.update { it.copy(error = error.message ?: "草稿清除失败") } }
        }
    }

    fun submit(onSuccess: (String) -> Unit = {}) {
        val snapshot = _state.value
        if (!snapshot.canSubmit) {
            val message = when {
                !snapshot.loggedIn -> "请先登录后再发布"
                snapshot.message.trim().isEmpty() -> "请输入动态内容"
                snapshot.characterCount > PublishUiState.MAX_MESSAGE_LENGTH -> "动态内容不能超过 ${PublishUiState.MAX_MESSAGE_LENGTH} 字"
                else -> "当前无法发布"
            }
            _state.update { it.copy(error = message) }
            return
        }
        // Stop a pending debounced save from restoring stale content after submit succeeds.
        val pendingDraftJob = draftJob
        pendingDraftJob?.cancel()
        val generation = ++submitGeneration
        _state.update { it.copy(submitting = true, error = null, notice = null, publishedFeedId = null) }
        viewModelScope.launch {
            pendingDraftJob?.join()
            val currentSession = runCatching { repository?.loadSessionState() }.getOrNull()
            if (generation != submitGeneration) return@launch
            val currentAccount = currentSession.accountKey()
            if (currentSession?.loggedIn != true || currentAccount != snapshot.accountKey) {
                if (currentAccount != snapshot.accountKey) {
                    runCatching { draftStore.save(snapshot.accountKey, snapshot.message) }
                } else {
                    scheduleDraftSave()
                }
                val currentDraft = if (currentAccount != snapshot.accountKey) {
                    runCatching { draftStore.load(currentAccount) }.getOrElse { "" }
                } else {
                    snapshot.message
                }
                _state.update {
                    it.copy(
                        submitting = false,
                        loggedIn = currentSession?.loggedIn == true,
                        accountKey = currentAccount,
                        message = currentDraft,
                        error = "登录状态已变化，请重新确认后再发布",
                    )
                }
                return@launch
            }
            val result = repository?.createFeed(snapshot.message.trim())
                ?: Result.failure(IllegalStateException("数据服务尚未就绪"))
            if (generation != submitGeneration) return@launch
            result.onSuccess { feedId ->
                draftJob?.cancel()
                runCatching { draftStore.clear(snapshot.accountKey) }
                _state.update { it.copy(submitting = false, message = "", publishedFeedId = feedId, notice = "发布成功", error = null) }
                onSuccess(feedId)
            }.onFailure { error ->
                _state.update { it.copy(submitting = false, error = publishErrorMessage(error)) }
                scheduleDraftSave()
            }
        }
    }

    private fun scheduleDraftSave() {
        draftJob?.cancel()
        val snapshot = _state.value
        draftJob = viewModelScope.launch {
            delay(600)
            runCatching { draftStore.save(snapshot.accountKey, snapshot.message) }
                .onFailure { error -> _state.update { it.copy(error = error.message ?: "草稿保存失败") } }
        }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PublishViewModel(
                        repositoryProvider = { HomeRepository(createPlatformGateway()) },
                        draftStore = AndroidPublishDraftStore(application),
                    ) as T
                }
            }
    }
}

private fun SessionState?.accountKey(): String = when {
    this?.uid?.isNotBlank() == true -> "uid:${this.uid}"
    this?.loggedIn == true -> "logged-in"
    else -> "guest"
}

private fun publishErrorMessage(error: Throwable): String {
    val raw = error.message.orEmpty()
    return when {
        raw.contains("captcha", ignoreCase = true) || raw.contains("验证码") -> "发布需要通过酷安的验证码验证，或登录官方客户端后重试"
        raw.contains("风控") || raw.contains("device", ignoreCase = true) -> "酷安服务端暂时拒绝此设备发布，请稍后重试"
        raw.isBlank() -> "发布失败，请稍后重试"
        else -> raw
    }
}
