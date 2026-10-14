package app.coolapk.kmp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.coolapk.kmp.core.HomeRepository
import com.coolapk.kmp.core.UserProfile
import com.coolapk.kmp.core.createPlatformGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 编辑资料：签名、性别、生日、所在地；只提交有变化的字段，字段与参考项目的接口保持一致。 */
internal data class ProfileEditUiState(
    val bio: String = "",
    /** 1 为男，0 为女。 */
    val gender: Int? = null,
    val birthYear: String = "",
    val birthMonth: String = "",
    val birthDay: String = "",
    val province: String = "",
    val city: String = "",
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

internal class ProfileEditViewModel(
    private val initial: UserProfile,
    private val repositoryProvider: () -> HomeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(
        ProfileEditUiState(
            bio = initial.bio.orEmpty(),
            gender = initial.gender?.takeIf { it == 0 || it == 1 },
            birthYear = initial.birthYear?.toString().orEmpty(),
            birthMonth = initial.birthMonth?.toString().orEmpty(),
            birthDay = initial.birthDay?.toString().orEmpty(),
            province = initial.province.orEmpty(),
            city = initial.city.orEmpty(),
        ),
    )
    val state: StateFlow<ProfileEditUiState> = _state.asStateFlow()
    private val repository by lazy { runCatching(repositoryProvider).getOrNull() }

    fun update(transform: (ProfileEditUiState) -> ProfileEditUiState) {
        _state.update(transform)
    }

    fun save() {
        val snapshot = _state.value
        val source = repository ?: run {
            _state.update { it.copy(error = "数据服务尚未就绪") }
            return
        }
        val year = snapshot.birthYear.trim().takeIf(String::isNotEmpty)?.toIntOrNull()
        val month = snapshot.birthMonth.trim().takeIf(String::isNotEmpty)?.toIntOrNull()
        val day = snapshot.birthDay.trim().takeIf(String::isNotEmpty)?.toIntOrNull()
        if ((snapshot.birthYear.isNotBlank() && year == null) ||
            (snapshot.birthMonth.isNotBlank() && month == null) ||
            (snapshot.birthDay.isNotBlank() && day == null)
        ) {
            _state.update { it.copy(error = "生日请填写数字") }
            return
        }
        val bioChanged = snapshot.bio.trim() != initial.bio.orEmpty().trim()
        val genderChanged = snapshot.gender != null && snapshot.gender != initial.gender
        val birthdayChanged = year != initial.birthYear || month != initial.birthMonth || day != initial.birthDay
        val locationChanged = snapshot.province.trim() != initial.province.orEmpty().trim() ||
            snapshot.city.trim() != initial.city.orEmpty().trim()
        if (!bioChanged && !genderChanged && !birthdayChanged && !locationChanged) {
            _state.update { it.copy(saved = true) }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val steps = buildList {
                if (bioChanged) add("bio" to snapshot.bio.trim())
                if (genderChanged) add("gender" to snapshot.gender.toString())
                if (birthdayChanged) {
                    add("" to """{"birthyear":${year ?: 0},"birthmonth":${month ?: 0},"birthday":${day ?: 0}}""")
                }
                if (locationChanged) {
                    add("" to """{"province":"${snapshot.province.trim()}","city":"${snapshot.city.trim()}"}""")
                }
            }
            for ((key, value) in steps) {
                val result = source.updateProfile(key, value)
                if (result.isFailure) {
                    _state.update { it.copy(saving = false, error = result.exceptionOrNull()?.message ?: "保存失败") }
                    return@launch
                }
            }
            _state.update { it.copy(saving = false, saved = true) }
        }
    }

    companion object {
        fun factory(profile: UserProfile) = viewModelFactory {
            initializer { ProfileEditViewModel(profile) { HomeRepository(createPlatformGateway()) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileEditScreen(
    profile: UserProfile,
    onBack: () -> Unit,
    viewModel: ProfileEditViewModel = viewModel(key = "profile-edit-${profile.uid}", factory = ProfileEditViewModel.factory(profile)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    LaunchedEffect(state.saved) { if (state.saved) onBack() }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("编辑资料") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = !state.saving) {
                        Text(if (state.saving) "保存中" else "保存")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.widthIn(max = 900.dp).fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = state.bio,
                    onValueChange = { value -> viewModel.update { it.copy(bio = value) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("个性签名") },
                    minLines = 2,
                )

                Text("性别", style = MaterialTheme.typography.titleSmall)
                val genders = listOf(1 to "男", 0 to "女")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    genders.forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = state.gender == value,
                            onClick = { viewModel.update { it.copy(gender = value) } },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = genders.size),
                            label = { Text(label) },
                        )
                    }
                }

                Text("生日", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.birthYear,
                        onValueChange = { value -> viewModel.update { it.copy(birthYear = value) } },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("年") },
                    )
                    OutlinedTextField(
                        value = state.birthMonth,
                        onValueChange = { value -> viewModel.update { it.copy(birthMonth = value) } },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("月") },
                    )
                    OutlinedTextField(
                        value = state.birthDay,
                        onValueChange = { value -> viewModel.update { it.copy(birthDay = value) } },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("日") },
                    )
                }

                Text("所在地", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.province,
                        onValueChange = { value -> viewModel.update { it.copy(province = value) } },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("省份") },
                    )
                    OutlinedTextField(
                        value = state.city,
                        onValueChange = { value -> viewModel.update { it.copy(city = value) } },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("城市") },
                    )
                }

                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
