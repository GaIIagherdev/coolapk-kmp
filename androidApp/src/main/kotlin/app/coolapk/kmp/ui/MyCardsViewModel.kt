package app.coolapk.kmp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.coolapk.kmp.core.HomeRepository
import com.coolapk.kmp.core.MyCard
import com.coolapk.kmp.core.createPlatformGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class MyCardsState(
    val loading: Boolean = true,
    val cards: List<MyCard> = emptyList(),
    val error: String? = null,
)

/** 「我的常去」：读取个人中心“我的卡片”配置中的常去分组，与参考项目 MyRecentPage 一致。 */
internal class MyCardsViewModel(
    private val load: suspend () -> List<MyCard>,
) : ViewModel() {
    private val _state = MutableStateFlow(MyCardsState())
    val state: StateFlow<MyCardsState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                _state.value = MyCardsState(loading = false, cards = load())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = MyCardsState(loading = false, error = error.message ?: "加载失败，请检查网络")
            }
        }
    }

    companion object {
        fun factory() = viewModelFactory {
            initializer {
                MyCardsViewModel { HomeRepository(createPlatformGateway()).loadMyCards(refresh = true) }
            }
        }
    }
}
