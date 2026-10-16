package app.coolapk.kmp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.coolapk.kmp.core.CompareRow
import com.coolapk.kmp.core.CompareSection
import com.coolapk.kmp.core.HomeRepository
import com.coolapk.kmp.core.ProductConfigJsonParser
import com.coolapk.kmp.core.ProductConfigItem
import com.coolapk.kmp.core.createPlatformGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class ProductCompareUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val items: List<ProductConfigItem> = emptyList(),
    val sections: List<CompareSection> = emptyList(),
)

internal class ProductCompareViewModel(
    private val configIds: List<String>,
    private val repositoryProvider: () -> HomeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ProductCompareUiState(loading = true))
    val state: StateFlow<ProductCompareUiState> = _state.asStateFlow()
    private val repository by lazy { runCatching(repositoryProvider).getOrNull() }
    private val parser = ProductConfigJsonParser()

    init {
        load()
    }

    fun load() {
        val source = repository ?: run {
            _state.update { it.copy(loading = false, error = "数据服务尚未就绪") }
            return
        }
        if (configIds.size < 2) {
            _state.update { it.copy(loading = false, error = "至少选择两款型号进行对比") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val items = source.loadProductConfigs(configIds)
                if (items.size < 2) {
                    _state.update { it.copy(loading = false, error = "型号数据加载不完整，请重试") }
                    return@launch
                }
                _state.update { it.copy(loading = false, items = items, sections = parser.buildSections(items)) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _state.update { it.copy(loading = false, error = failure.message ?: "对比数据加载失败") }
            }
        }
    }

    companion object {
        fun factory(configIds: List<String>) = viewModelFactory {
            initializer { ProductCompareViewModel(configIds) { HomeRepository(createPlatformGateway()) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProductCompareScreen(
    configIds: List<String>,
    titles: List<String> = emptyList(),
    onBack: () -> Unit,
    viewModel: ProductCompareViewModel = viewModel(
        key = "compare-${configIds.joinToString(",")}",
        factory = ProductCompareViewModel.factory(configIds),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("产品对比") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            when {
                state.loading && state.items.isEmpty() -> ExpressiveLoadingIndicator(Modifier.align(Alignment.Center))
                state.error != null && state.items.isEmpty() -> CoolapkErrorState(
                    message = state.error ?: "加载失败",
                    onRetry = viewModel::load,
                    modifier = Modifier.fillMaxWidth(),
                )
                else -> CompareTable(
                    configs = state.items,
                    sections = state.sections,
                    headers = state.items.mapIndexed { index, item -> titles.getOrNull(index) ?: item.title },
                )
            }
        }
    }
}

@Composable
private fun CompareTable(configs: List<ProductConfigItem>, sections: List<CompareSection>, headers: List<String>) {
    val columnWidth = 150.dp
    val labelWidth = 104.dp
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
        Column(Modifier.width(labelWidth + columnWidth * configs.size)) {
            // 型号表头
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
                Box(Modifier.width(labelWidth))
                headers.forEach { header ->
                    Text(
                        header,
                        modifier = Modifier.width(columnWidth).padding(end = 8.dp),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                sections.forEach { section ->
                    item(key = "section-${section.title}") {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                section.title,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                    rowItems(section.title, section.rows)
                }
            }
        }
    }
}

private fun LazyListScope.rowItems(sectionTitle: String, rows: List<CompareRow>) {
    items(rows, key = { row: CompareRow -> "$sectionTitle-${row.key}" }) { row: CompareRow ->
        val different = row.values.filter(String::isNotBlank).distinct().size > 1
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    row.label,
                    modifier = Modifier.width(104.dp).padding(end = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                row.values.forEach { value ->
                    Text(
                        value.ifBlank { "—" },
                        modifier = Modifier.width(150.dp).padding(end = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (different) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (different) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
