package app.coolapk.kmp.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.coolapk.kmp.core.AppDetail
import com.coolapk.kmp.core.AppDetailJsonParser
import com.coolapk.kmp.core.AppItem
import com.coolapk.kmp.core.HomeRepository
import com.coolapk.kmp.core.createPlatformGateway

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppDetailScreen(
    app: AppItem,
    onBack: () -> Unit,
    onOpenImage: (List<String>, Int) -> Unit,
) {
    var detail by remember(app.id) { mutableStateOf<AppDetail?>(null) }
    var loading by remember(app.id) { mutableStateOf(true) }
    var error by remember(app.id) { mutableStateOf<String?>(null) }
    var reload by remember(app.id) { mutableIntStateOf(0) }
    val context = LocalContext.current

    LaunchedEffect(app.id, reload) {
        loading = true
        error = null
        runCatching {
            val raw = HomeRepository(createPlatformGateway()).getAppDetailRaw(app.packageName ?: app.id)
            AppDetailJsonParser().parse(raw) ?: throw IllegalStateException("应用详情数据不完整")
        }.onSuccess { detail = it }
            .onFailure { error = it.message ?: "应用详情加载失败" }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(app.name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        when {
            loading && detail == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { ExpressiveLoadingIndicator() }

            detail == null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(error ?: "应用详情暂不可用", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { reload++ }) { Text("重试") }
            }

            else -> {
                val info = detail ?: return@Scaffold
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier.widthIn(max = 780.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(app.copy(iconUrl = info.iconUrl ?: app.iconUrl))
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(info.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                                info.developer?.let {
                                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                info.packageName?.let {
                                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    item {
                        Column(Modifier.widthIn(max = 780.dp).fillMaxWidth()) {
                            val hasStats = info.score != null || info.downloadCount != null || info.size != null
                            if (hasStats) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    info.score?.let {
                                        InfoPill("评分 $it", icon = Icons.Rounded.Star)
                                    }
                                    info.downloadCount?.let {
                                        InfoPill(
                                            "下载 $it",
                                            icon = Icons.Rounded.Download,
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        )
                                    }
                                    info.size?.let {
                                        InfoPill(
                                            it,
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(14.dp))
                            }
                            val version = listOfNotNull(
                                info.version?.let { "版本 $it" },
                                info.updatedAt?.let { "更新 ${it.toDisplayTime()}" },
                            ).joinToString("  ·  ")
                            if (version.isNotBlank()) {
                                Text(version, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(14.dp))
                            }
                            Button(onClick = {
                                val id = info.packageName ?: app.packageName ?: app.id
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.coolapk.com/apk/${Uri.encode(id)}")))
                            }) {
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("在酷安打开")
                            }
                        }
                    }
                    if (info.screenshots.isNotEmpty()) {
                        item {
                            Column(Modifier.widthIn(max = 780.dp).fillMaxWidth()) {
                                SectionHeader("截图", horizontalPadding = 0.dp)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    itemsIndexed(info.screenshots) { index, url ->
                                        AsyncImage(
                                            model = coolapkImageRequest(url),
                                            contentDescription = "第 ${index + 1} 张应用截图",
                                            modifier = Modifier.width(170.dp).height(300.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable(onClickLabel = "查看第 ${index + 1} 张截图") {
                                                    onOpenImage(info.screenshots, index)
                                                },
                                            contentScale = ContentScale.Fit,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    info.description?.takeIf(String::isNotBlank)?.let { description ->
                        item { AppDetailTextSection("应用介绍", description) }
                    }
                    info.changeLog?.takeIf(String::isNotBlank)?.let { changeLog ->
                        item { AppDetailTextSection("更新日志", changeLog) }
                    }
                    error?.let { message ->
                        item {
                            Text(message, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppDetailTextSection(title: String, content: String) {
    Column(Modifier.widthIn(max = 780.dp).fillMaxWidth()) {
        SectionHeader(title, horizontalPadding = 0.dp)
        Spacer(Modifier.height(4.dp))
        Text(content.toPlainText(), style = MaterialTheme.typography.bodyMedium)
    }
}
