package com.starlr.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.starlr.app.AppContainer
import com.starlr.app.data.ChangePasswordRequest
import com.starlr.app.data.StarlrRepository
import com.starlr.app.data.UpdateMeRequest
import com.starlr.app.data.User
import com.starlr.app.ui.components.Avatar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SettingsState(
    val me: User? = null,
    val saving: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

/** 设置页 VM：读取/更新资料、改密码、退出登录 */
class SettingsViewModel(private val repo: StarlrRepository) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(me = repo.loadCurrentUser())
        }
    }

    fun save(nickname: String, bio: String) {
        if (_state.value.saving) return
        _state.value = _state.value.copy(saving = true, message = null, error = null)
        viewModelScope.launch {
            runCatching { repo.api.updateMe(UpdateMeRequest(nickname = nickname.trim(), bio = bio.trim())) }
                .onSuccess { res ->
                    _state.value = _state.value.copy(saving = false, me = res.user, message = "资料已更新 ✦")
                    repo.loadCurrentUser()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        saving = false,
                        error = StarlrRepository.toApiError(e).message,
                    )
                }
        }
    }

    fun changePassword(current: String, new: String) {
        if (_state.value.saving) return
        _state.value = _state.value.copy(saving = true, message = null, error = null)
        viewModelScope.launch {
            runCatching { repo.api.changePassword(ChangePasswordRequest(current, new)) }
                .onSuccess {
                    _state.value = _state.value.copy(saving = false, message = "密码已更新，下次登录请使用新密码")
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        saving = false,
                        error = StarlrRepository.toApiError(e).message,
                    )
                }
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            repo.logout()
            onLoggedOut()
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(container.repository) as T
    }
}

/**
 * 设置页：资料编辑（昵称/简介）+ 修改密码 + 退出登录。
 */
@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(container))
    val state by vm.state.collectAsState()
    val session by container.repository.session.collectAsState()

    var nickname by rememberSaveable { mutableStateOf("") }
    var bio by rememberSaveable { mutableStateOf("") }
    var currentPassword by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }

    // 首次载入时预填
    LaunchedEffect(state.me) {
        state.me?.let {
            if (nickname.isEmpty() && bio.isEmpty()) {
                nickname = it.nickname.orEmpty()
                bio = it.bio.orEmpty()
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("设置", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            // 账号卡片
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(
                    url = state.me?.avatarUrl ?: session.user?.avatarUrl,
                    name = session.user?.username ?: "星",
                    size = 56.dp,
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        session.user?.nickname?.takeIf { it.isNotBlank() }
                            ?: session.user?.username ?: "未登录",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                    )
                    Text(
                        "@${session.user?.username ?: "guest"}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // 资料
            Text("资料", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text("昵称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
            )
            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text("简介") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
            )
            Button(
                onClick = { vm.save(nickname, bio) },
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(999.dp),
            ) {
                if (state.saving) CircularProgressIndicator(Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                else Text("保存资料")
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // 密码
            Text("修改密码", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            OutlinedTextField(
                value = currentPassword,
                onValueChange = { currentPassword = it },
                label = { Text("当前密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            )
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text("新密码（至少 8 位）") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            )
            Button(
                onClick = { vm.changePassword(currentPassword, newPassword) },
                enabled = !state.saving && currentPassword.length >= 8 && newPassword.length >= 8,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            ) { Text("更新密码") }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            state.message?.let {
                Text(it, color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp)
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            // 退出登录
            Button(
                onClick = { vm.logout(onLoggedOut) },
                enabled = session.loggedIn,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) { Text("退出登录") }
            Spacer(Modifier.height(32.dp))
        }
    }
}
