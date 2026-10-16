package app.coolapk.kmp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coolapk.kmp.core.FeedItem

/** 问题详情：问题正文与回答列表（可切换热门、高赞、最新）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuestionDetailScreen(
    questionId: String,
    onBack: () -> Unit,
    onOpenFeed: (FeedItem) -> Unit,
    onLike: (FeedItem) -> Unit,
    likeOverrides: Map<String, Boolean>,
    viewModel: QuestionDetailViewModel = viewModel(
        key = "question-$questionId",
        factory = QuestionDetailViewModel.factory(questionId),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("问题", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            state.question?.let { question ->
                Column(Modifier.widthIn(max = 900.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    question.title?.takeIf(String::isNotBlank)?.let { title ->
                        Text(title, style = MaterialTheme.typography.titleMedium)
                    }
                    if (question.message.isNotBlank()) {
                        Text(question.message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            state.questionError?.let { message ->
                if (state.question == null) {
                    Text(
                        message,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            val sorts = AnswerSort.entries
            SecondaryScrollableTabRow(
                selectedTabIndex = sorts.indexOf(state.sort),
                modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth(),
                edgePadding = 16.dp,
            ) {
                sorts.forEach { sort ->
                    Tab(
                        selected = state.sort == sort,
                        onClick = { viewModel.select(sort) },
                        text = { Text(sort.label, maxLines = 1) },
                    )
                }
            }
            PagedColumn(
                list = state.answers,
                emptyTitle = "还没有回答",
                emptyDetail = "这个问题还没有人回答。",
                key = { it.id },
                onLoadMore = viewModel::loadMore,
                onRetry = viewModel::retry,
            ) { answer ->
                FeedCard(
                    item = answer,
                    liked = likeOverrides[answer.id] ?: answer.isLiked,
                    onLike = { onLike(answer) },
                    onOpenDetail = { onOpenFeed(answer) },
                    onReply = { onOpenFeed(answer) },
                    onOpenImage = { _, _ -> },
                    card = true,
                )
            }
        }
    }
}
