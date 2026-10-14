package app.coolapk.kmp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.coolapk.kmp.core.DigitalProductDetail
import com.coolapk.kmp.core.HomeRepository
import com.coolapk.kmp.core.createPlatformGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class DigitalProductDetailUiState(
    val productId: String? = null,
    val detail: DigitalProductDetail? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

internal class DigitalProductDetailViewModel(
    private val repositoryProvider: () -> HomeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DigitalProductDetailUiState())
    val state: StateFlow<DigitalProductDetailUiState> = _state.asStateFlow()

    private val repository by lazy { runCatching(repositoryProvider).getOrNull() }
    private var requestId = 0

    fun load(productId: String, force: Boolean = false) {
        val id = productId.trim()
        val previous = _state.value
        if (id.isEmpty()) {
            ++requestId
            _state.value = DigitalProductDetailUiState(error = "产品 ID 无效")
            return
        }
        if (!force && previous.productId == id && (previous.loading || previous.detail != null)) return

        val request = ++requestId
        _state.value = DigitalProductDetailUiState(
            productId = id,
            detail = previous.detail?.takeIf { previous.productId == id },
            loading = true,
        )
        viewModelScope.launch {
            try {
                val detail = repository?.loadDigitalProductDetail(id)
                    ?: error("Rust 核心尚未就绪，请先构建 Android 原生库")
                if (request != requestId) return@launch
                _state.update { it.copy(detail = detail, loading = false, error = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (request != requestId) return@launch
                _state.update {
                    it.copy(loading = false, error = failure.message?.takeIf(String::isNotBlank)
                        ?: "产品详情加载失败")
                }
            }
        }
    }

    fun retry() {
        _state.value.productId?.let { load(it, force = true) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { DigitalProductDetailViewModel { HomeRepository(createPlatformGateway()) } }
        }
    }
}
