package com.starlr.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.remember
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
import com.starlr.app.ui.components.UploadingIndicator
import com.starlr.app.ui.components.rememberImageUploadLauncher
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

    /** 上传后把新 url 写入用户资料（头像由后端自动更新 avatar_url；背景需 PATCH /api/me） */
    fun refreshMe() {
        viewModelScope.launch { _state.value = _state.value.copy(me = repo.loadCurrentUser()) }
    }

    fun updateBackground(url: String?) {
        if (_state.value.saving) return
        _state.value = _state.value.copy(saving = true, message = null, error = null)
        viewModelScope.launch {
            runCatching { repo.api.updateMe(UpdateMeRequest(backgroundUrl = url)) }
                .onSuccess { res ->
                    _state.value = _state.value.copy(saving = false, me = res.user)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(saving = false, error = StarlrRepository.toApiError(e).message)
                }
        }
    }

    fun save(nickname: String, bio: String) {
        if (_state.value.saving) return
        _state.value = _state.value.copy(saving = true, message = null, error = null)
        viewModelScope.launch {
            runCatching { repo.api.updateMe(UpdateMeRequest(nickname = nickname.trim(), bio = bio.trim())) }
                .onSuccess { res ->
                    _state.value = _state.value.copy(saving = false, me = res.user, message = "资料已更新")
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

    fun saveServer(serverConfig: com.starlr.app.data.ServerConfig, url: String) {
        val err = serverConfig.set(url)
        if (err != null) {
            _state.value = _state.value.copy(error = err, message = null)
            return
        }
        repo.resetSession()
        _state.value = _state.value.copy(message = "服务器地址已保存，已退出当前登录", error = null)
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
    var serverUrl by rememberSaveable { mutableStateOf(container.serverConfig.current) }
    var uploadError by remember { mutableStateOf<String?>(null) }
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmSaveServer by remember { mutableStateOf(false) }

    // 头像上传：成功后刷新 me（后端自动更新 avatar_url）
    val pickAvatar = rememberImageUploadLauncher(
        repo = container.repository,
        purpose = "avatar",
        maxSizeMB = 2,
        onError = { uploadError = it },
    ) { _ -> vm.refreshMe() }

    // 背景图上传：成功后 PATCH /api/me 写入 backgroundUrl
    val pickBackground = rememberImageUploadLauncher(
        repo = container.repository,
        purpose = "background",
        maxSizeMB = 5,
        onError = { uploadError = it },
    ) { url -> vm.updateBackground(url) }

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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(16.dp)),
            ) {
                com.starlr.app.ui.components.ProfileBanner(
                    backgroundUrl = com.starlr.app.ui.components.mediaUrl(
                        state.me?.backgroundUrl ?: session.user?.backgroundUrl,
                        com.starlr.app.ui.BuildConfigAware.baseUrl,
                    ),
                    modifier = Modifier.matchParentSize(),
                )
                Avatar(
                    url = com.starlr.app.ui.components.mediaUrl(state.me?.avatarUrl ?: session.user?.avatarUrl, com.starlr.app.ui.BuildConfigAware.baseUrl),
                    name = session.user?.username ?: "用户",
                    size = 52.dp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(MaterialTheme.colorScheme.background, androidx.compose.foundation.shape.CircleShape)
                        .padding(2.dp),
                )
                Text(
                    session.user?.nickname?.takeIf { it.isNotBlank() } ?: session.user?.username ?: "",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(start = 56.dp, end = 12.dp, bottom = 12.dp),
                )
                // 换背景图
                IconButton(
                    onClick = { pickBackground() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.45f), androidx.compose.foundation.shape.CircleShape),
                ) {
                    Icon(
                        Icons.Outlined.Wallpaper,
                        contentDescription = "更换主页背景图",
                        tint = Color.White,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Avatar(
                        url = com.starlr.app.ui.components.mediaUrl(state.me?.avatarUrl ?: session.user?.avatarUrl, com.starlr.app.ui.BuildConfigAware.baseUrl),
                        name = session.user?.username ?: "用户",
                        size = 56.dp,
                    )
                    // 换头像（右上角小相机）：视觉 24dp，命中区扩大到 48dp
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 12.dp, y = (-12).dp)
                            .size(48.dp)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null,
                            ) { pickAvatar() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.AddAPhoto,
                                contentDescription = "更换头像",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
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

            uploadError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

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
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
            )
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text("新密码（至少 8 位）") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
            )
            Button(
                onClick = { vm.changePassword(currentPassword, newPassword) },
                enabled = !state.saving && currentPassword.length >= 8 && newPassword.length >= 8,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            ) { Text("更新密码") }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // 服务器
            Text("服务器", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("API 地址") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
            )
            Button(
                onClick = { confirmSaveServer = true },
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(999.dp),
            ) { Text("保存地址") }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            state.message?.let {
                Text(it, color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp)
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            // 退出登录
            Button(
                onClick = { confirmLogout = true },
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

    // 退出登录确认弹窗（保存服务器地址会静默登出，同样需要确认）
    if (confirmLogout) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("退出登录", fontWeight = FontWeight.Bold) },
            text = { Text("确定要退出当前账号吗？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    vm.logout(onLoggedOut)
                }) { Text("退出") }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("取消") } },
        )
    }
    if (confirmSaveServer) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmSaveServer = false },
            title = { Text("保存服务器地址", fontWeight = FontWeight.Bold) },
            text = { Text("保存新的服务器地址会退出当前登录，需要重新登录。确定继续吗？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSaveServer = false
                    vm.saveServer(container.serverConfig, serverUrl)
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { confirmSaveServer = false }) { Text("取消") } },
        )
    }
}
