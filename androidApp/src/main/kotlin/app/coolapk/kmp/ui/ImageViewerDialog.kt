package app.coolapk.kmp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.SubcomposeAsyncImage
import app.coolapk.kmp.ui.theme.CoolapkGreenDark
import kotlinx.coroutines.launch

internal class ImageViewerSelection private constructor(
    val urls: List<String>,
    val initialIndex: Int,
) {
    companion object {
        fun create(urls: List<String>, initialIndex: Int): ImageViewerSelection? {
            if (urls.isEmpty() || initialIndex !in urls.indices) return null
            return ImageViewerSelection(urls.toList(), initialIndex)
        }
    }
}

@Composable
internal fun ImageViewerDialog(selection: ImageViewerSelection, onDismiss: () -> Unit) {
    val pagerState = rememberPagerState(initialPage = selection.initialIndex) { selection.urls.size }
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(Modifier.fillMaxSize().background(Color(0xFF101512))) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                ImageViewerPage(selection.urls[page], page, selection.urls.size)
            }

            Row(
                modifier = Modifier.align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalIconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭图片")
                }
                Text("图片", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }

            Row(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                FilledTonalIconButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                    enabled = pagerState.currentPage > 0,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "上一张")
                }
                Text(
                    "${pagerState.currentPage + 1} / ${selection.urls.size}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                FilledTonalIconButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                    enabled = pagerState.currentPage < selection.urls.lastIndex,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "下一张")
                }
            }
        }
    }
}

@Composable
private fun ImageViewerPage(url: String, index: Int, count: Int) {
    var retry by remember(url) { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        key(retry) {
            SubcomposeAsyncImage(
                model = coolapkImageRequest(url),
                contentDescription = "第 ${index + 1} 张图片，共 $count 张",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CoolapkGreenDark)
                    }
                },
                error = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("图片加载失败", color = Color.White)
                        TextButton(onClick = { retry++ }) { Text("重试", color = CoolapkGreenDark) }
                    }
                },
            )
        }
    }
}
