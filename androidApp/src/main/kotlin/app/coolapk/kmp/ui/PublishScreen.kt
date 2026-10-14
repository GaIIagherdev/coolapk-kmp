package app.coolapk.kmp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PublishScreen(
    onBack: () -> Unit,
    viewModel: PublishViewModel,
    onPublished: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var discardConfirmation by remember { mutableStateOf(false) }
    var clearConfirmation by remember { mutableStateOf(false) }
    val leave: () -> Unit = {
        if (state.hasUnsavedContent && !state.submitting) discardConfirmation = true else onBack()
    }

    BackHandler(enabled = !state.submitting && !state.savingDraft, onBack = leave)
    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissNotice()
        }
    }
    Scaffold(
        modifier = Modifier.imePadding(),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("发布动态") },
                navigationIcon = {
                    IconButton(onClick = leave, enabled = !state.submitting && !state.savingDraft) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.saveDraftNow() },
                        enabled = !state.loadingDraft && !state.submitting && !state.savingDraft,
                    ) {
                        Icon(Icons.Rounded.Save, contentDescription = "保存草稿")
                    }
                },
            )
        },
        bottomBar = {
            PublishBottomBar(
                state = state,
                onClearDraft = { clearConfirmation = true },
                onSubmit = { viewModel.submit(onPublished) },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            if (state.loadingDraft) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                PublishBody(
                    state = state,
                    onMessageChange = viewModel::setMessage,
                    onTogglePreview = viewModel::togglePreview,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (discardConfirmation) {
        AlertDialog(
            onDismissRequest = { discardConfirmation = false },
            title = { Text("保留这份草稿？") },
            text = { Text("返回后内容会自动保存在当前账号的草稿中。") },
            dismissButton = {
                TextButton(onClick = { discardConfirmation = false }) { Text("继续编辑") }
            },
            confirmButton = {
                TextButton(onClick = {
                    discardConfirmation = false
                    viewModel.saveDraftNow(onBack)
                }) { Text("返回") }
            },
        )
    }
    if (clearConfirmation) {
        AlertDialog(
            onDismissRequest = { clearConfirmation = false },
            title = { Text("清除草稿？") },
            text = { Text("当前账号保存的发布内容会被删除，且无法恢复。") },
            dismissButton = {
                TextButton(onClick = { clearConfirmation = false }) { Text("取消") }
            },
            confirmButton = {
                TextButton(onClick = { clearConfirmation = false; viewModel.clearDraft() }) { Text("清除") }
            },
        )
    }
}

@Composable
private fun PublishBody(
    state: PublishUiState,
    onMessageChange: (String) -> Unit,
    onTogglePreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(max = 860.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = !state.preview,
                onClick = { if (state.preview) onTogglePreview() },
                label = { Text("编辑") },
            )
            FilterChip(
                selected = state.preview,
                onClick = { if (!state.preview) onTogglePreview() },
                label = { Text("预览") },
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${state.characterCount}/${PublishUiState.MAX_MESSAGE_LENGTH}",
                color = if (state.characterCount > PublishUiState.MAX_MESSAGE_LENGTH) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }

        if (state.preview) {
            PublishPreview(state.message)
        } else {
            OutlinedTextField(
                value = state.message,
                onValueChange = onMessageChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 9,
                maxLines = 16,
                label = { Text("动态内容") },
                placeholder = { Text("分享这一刻的酷搞感受，与酷友讨论数码生活…") },
                supportingText = { Text("纯文本动态，发布前可切换到预览") },
                isError = state.characterCount > PublishUiState.MAX_MESSAGE_LENGTH,
                enabled = !state.submitting,
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("发布状态", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        state.loggedIn -> "已登录 · ${state.accountKey.removePrefix("uid:").ifBlank { "当前账号" }}"
                        else -> "未登录，登录后才能发布动态"
                    },
                    color = if (state.loggedIn) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        state.error?.let { message ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    message,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun PublishPreview(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("预览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (message.isBlank()) {
                Text("输入内容后这里会显示动态预览", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(message, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun PublishBottomBar(
    state: PublishUiState,
    onClearDraft: () -> Unit,
    onSubmit: () -> Unit,
) {
    Surface(
        modifier = Modifier.navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onClearDraft, enabled = state.hasUnsavedContent && !state.submitting && !state.savingDraft) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("清除草稿")
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = onSubmit, enabled = state.canSubmit, contentPadding = PaddingValues(horizontal = 22.dp)) {
                    if (state.submitting) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (state.submitting) "发布中" else "发布")
                }
            }
        }
    }
}
