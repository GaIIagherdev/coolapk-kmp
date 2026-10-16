package app.coolapk.kmp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.coolapk.kmp.core.FeedItem
import com.coolapk.kmp.core.HomeCursor
import com.coolapk.kmp.core.HomePage
import com.coolapk.kmp.core.HomeRepository
import com.coolapk.kmp.core.createPlatformGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class AnswerSort(val key: String, val label: String) {
    HOT("reply", "热门"),
    LIKE("like", "高赞"),
    LATEST("dateline", "最新"),
}

internal data class QuestionDetailUiState(
    val question: FeedItem? = null,
    val questionError: String? = null,
    val sort: AnswerSort = AnswerSort.HOT,
    val answers: PagedListState<FeedItem> = PagedListState(),
)

internal class QuestionDetailViewModel(
    private val questionId: String,
    private val repositoryProvider: () -> HomeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(QuestionDetailUiState())
    val state: StateFlow<QuestionDetailUiState> = _state.asStateFlow()
    private val repository by lazy { runCatching(repositoryProvider).getOrNull() }

    init {
        loadQuestion()
        loadAnswers(page = 1)
    }

    fun select(sort: AnswerSort) {
        if (_state.value.sort == sort) return
        _state.update { it.copy(sort = sort, answers = PagedListState()) }
        loadAnswers(page = 1)
    }

    fun loadMore() {
        val snapshot = _state.value.answers
        if (snapshot.loading || snapshot.page == 0 || !snapshot.hasMore) return
        loadAnswers(page = snapshot.page + 1)
    }

    fun retry() {
        val snapshot = _state.value.answers
        if (snapshot.loading) return
        if (_state.value.question == null) loadQuestion()
        loadAnswers(page = if (snapshot.page == 0) 1 else snapshot.page + 1)
    }

    private fun loadQuestion() {
        val source = repository ?: run {
            _state.update { it.copy(questionError = "数据服务尚未就绪") }
            return
        }
        viewModelScope.launch {
            try {
                val question = source.loadFeedDetail(questionId)
                _state.update { it.copy(question = question, questionError = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _state.update { it.copy(questionError = failure.message ?: "加载问题失败") }
            }
        }
    }

    private fun loadAnswers(page: Int) {
        val source = repository ?: run {
            _state.update { it.copy(answers = it.answers.copy(error = "数据服务尚未就绪")) }
            return
        }
        val sort = _state.value.sort
        val cursor = if (page == 1) null else _state.value.answers.cursor
        _state.update { it.copy(answers = it.answers.copy(loading = true, error = null)) }
        viewModelScope.launch {
            try {
                val result = source.loadQuestionAnswers(questionId, sort.key, page, cursor)
                _state.update { state ->
                    if (state.sort != sort) return@update state
                    state.copy(answers = state.answers.merged(page, result))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _state.update { it.copy(answers = it.answers.copy(loading = false, error = failure.message ?: "加载回答失败")) }
            }
        }
    }

    private fun PagedListState<FeedItem>.merged(page: Int, result: HomePage): PagedListState<FeedItem> {
        val existing = if (page == 1) emptySet() else items.map(FeedItem::id).toSet()
        val fresh = result.items.filterNot { it.id in existing }.distinctBy(FeedItem::id)
        val cursor = result.firstItem?.let { first -> result.lastItem?.let { last -> HomeCursor(first, last) } }
        return copy(
            items = if (page == 1) fresh else items + fresh,
            loading = false,
            error = null,
            page = page,
            cursor = cursor,
            hasMore = result.hasMore && fresh.isNotEmpty(),
        )
    }

    companion object {
        fun factory(questionId: String) = viewModelFactory {
            initializer { QuestionDetailViewModel(questionId) { HomeRepository(createPlatformGateway()) } }
        }
    }
}
