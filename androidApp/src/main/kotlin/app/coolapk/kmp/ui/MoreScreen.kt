package app.coolapk.kmp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SportsEsports

private data class MoreItem(
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/**
 * 更多：公开内容的分组入口，结构与参考项目的「更多」页一致（应用与游戏、数码与装备、内容与社区）。
 * 账号相关入口留在「我的」页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoreScreen(
    onBack: () -> Unit,
    onOpenAppCenter: () -> Unit,
    onOpenGameCenter: () -> Unit,
    onOpenSelector: () -> Unit,
    onOpenSecondHand: () -> Unit,
    onOpenReviews: () -> Unit,
    onOpenEvents: () -> Unit,
    onOpenAlbums: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val sections = listOf(
        "应用与游戏" to listOf(
            MoreItem("应用中心", Icons.Rounded.Apps, onOpenAppCenter),
            MoreItem("游戏中心", Icons.Rounded.SportsEsports, onOpenGameCenter),
        ),
        "数码与装备" to listOf(
            MoreItem("机型对比", Icons.Rounded.Compare, onOpenSelector),
            MoreItem("二手市场", Icons.Rounded.ShoppingBag, onOpenSecondHand),
            MoreItem("评测专区", Icons.Rounded.RateReview, onOpenReviews),
        ),
        "内容与社区" to listOf(
            MoreItem("酷友圈活动", Icons.Rounded.Event, onOpenEvents),
            MoreItem("相册", Icons.Rounded.PhotoLibrary, onOpenAlbums),
        ),
    )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("更多") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            sections.forEach { (title, items) ->
                item(key = title) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader(title = title, horizontalPadding = 4.dp)
                        GroupedSurface {
                            items.forEachIndexed { index, item ->
                                if (index > 0) GroupedDivider()
                                MoreRow(item)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreRow(item: MoreItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = item.onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            item.title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
