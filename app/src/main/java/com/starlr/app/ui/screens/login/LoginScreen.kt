package com.starlr.app.ui.screens.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.starlr.app.AppContainer
import com.starlr.app.AuthBridgeActivity
import com.starlr.app.R
import com.starlr.app.data.AppLoginPrepareRequest
import com.starlr.app.data.AppLoginPrepareResponse
import com.starlr.app.data.StarlrRepository
import com.starlr.app.ui.theme.SlBrand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.net.URLEncoder

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

private fun openAuthBrowser(context: android.content.Context, url: String) {
    try {
        val tabsIntent = androidx.browser.customtabs.CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(
                androidx.browser.customtabs.CustomTabColorSchemeParams.Builder()
                    .setToolbarColor(android.graphics.Color.parseColor("#090C1F"))
                    .build(),
            )
            .build()
        tabsIntent.intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        tabsIntent.launchUrl(context, android.net.Uri.parse(url))
    } catch (e: Exception) {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** 登录 / 注册 共用 VM（两个屏共用一个实例会出现状态串扰，这里每屏独立创建） */
class AuthViewModel(private val container: AppContainer) : ViewModel() {

    private val repo = container.repository

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state

    fun login(identifier: String, password: String, onDone: () -> Unit) {
        submit(
            mode = "login",
            prepare = {
                repo.api.prepareAppLogin(
                    AppLoginPrepareRequest(mode = "login", password = password, identifier = identifier.trim()),
                )
            },
        ) { repo.login(identifier, password, it) }
    }

    fun register(username: String, email: String, password: String, nickname: String?) {
        submit(
            mode = "register",
            prepare = {
                repo.api.prepareAppLogin(
                    AppLoginPrepareRequest(
                        mode = "register",
                        password = password,
                        username = username.trim(),
                        email = email.trim(),
                        nickname = nickname?.trim()?.ifEmpty { null },
                    ),
                )
            },
        ) { repo.register(username, email, password, nickname, it) }
    }

    private fun submit(
        mode: String,
        prepare: suspend () -> AppLoginPrepareResponse,
        direct: suspend (String?) -> Unit,
    ) {
        if (_state.value.loading) return
        _state.value = AuthUiState(loading = true)
        viewModelScope.launch {
            try {
                val cfg = runCatching { repo.api.siteConfig() }.getOrNull()
                if (cfg?.turnstileSiteKey != null) {
                    // 生产启用人机验证：应用内先校验凭据换一次性预授权（preauth），再跳系统浏览器
                    // 完成一次 Turnstile 确认后带 ticket 回跳——密码全程只输一遍。
                    // 旧后端无此接口（404）时退回旧行为：直接跳网页表单登录。
                    val preauthUrl = runCatching { prepare() }
                        .map { pre ->
                            val query = buildString {
                                append("preauth=")
                                append(URLEncoder.encode(pre.preauth, "UTF-8"))
                                append("&mode=")
                                append(mode)
                                pre.name?.takeIf { it.isNotEmpty() }?.let {
                                    append("&name=")
                                    append(URLEncoder.encode(it, "UTF-8"))
                                }
                            }
                            repo.appAuthUrl + "?" + query
                        }
                        .getOrElse { e ->
                            if (StarlrRepository.toApiError(e).httpStatus == 404) null else throw StarlrRepository.toApiError(e)
                        }
                    if (preauthUrl != null) {
                        container.appContext.startActivity(
                            android.content.Intent(container.appContext, AuthBridgeActivity::class.java)
                                .putExtra(AuthBridgeActivity.EXTRA_URL, preauthUrl)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    } else {
                        openAuthBrowser(
                            container.appContext,
                            repo.appAuthUrl + if (mode == "register") "?mode=register" else "",
                        )
                    }
                    _state.value = AuthUiState(
                        message = if (preauthUrl != null) {
                            "将在浏览器完成一次人机验证（无需再次输入密码），完成后自动返回应用"
                        } else {
                            "已在浏览器打开登录页，登录后自动返回应用"
                        },
                    )
                } else {
                    direct(null)
                    _state.value = AuthUiState(loading = false)
                }
            } catch (e: Exception) {
                _state.value = AuthUiState(loading = false, error = StarlrRepository.toApiError(e).message)
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AuthViewModel(container) as T
    }
}

/**
 * 登录页 —— 品牌头部 + 用户名/邮箱 + 密码。
 */
@Composable
fun LoginScreen(
    container: AppContainer,
    onDone: () -> Unit,
) {
    val vm: AuthViewModel = viewModel(factory = AuthViewModel.Factory(container))
    val state by vm.state.collectAsState()
    val session by container.repository.session.collectAsState()

    var mode by rememberSaveable { mutableStateOf("login") } // login | register
    var identifier by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var nickname by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    // 登录成功 → 关闭登录页
    androidx.compose.runtime.LaunchedEffect(session.loggedIn) {
        if (session.loggedIn) onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(84.dp))
        // 品牌区（注意：不能用 R.mipmap.ic_launcher——API 26+ 解析为 adaptive icon XML，
        // painterResource 只支持矢量/位图，会抛 IllegalArgumentException 闪退，故用 drawable-nodpi 位图）
        Image(
            painter = painterResource(R.drawable.login_logo),
            contentDescription = null,
            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)),
        )
        Spacer(Modifier.height(10.dp))
        Text("Starlr", fontSize = 32.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(44.dp))

        if (mode == "register") {
            OutlinedTextField(
                value = identifier,
                onValueChange = { identifier = it },
                label = { Text("用户名（3-20 位字母数字）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SlBrand, cursorColor = SlBrand),
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text("昵称（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SlBrand, cursorColor = SlBrand),
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("邮箱") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SlBrand, cursorColor = SlBrand),
            )
            Spacer(Modifier.height(14.dp))
        } else {
            OutlinedTextField(
                value = identifier,
                onValueChange = { identifier = it },
                label = { Text("用户名或邮箱") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SlBrand, cursorColor = SlBrand),
            )
            Spacer(Modifier.height(14.dp))
        }
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码（8 位以上）") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SlBrand, cursorColor = SlBrand),
        )

        state.error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        state.message?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                if (mode == "login") vm.login(identifier, password, onDone)
                else vm.register(identifier, email, password, nickname)
            },
            enabled = !state.loading && identifier.isNotBlank() && password.isNotBlank() &&
                (mode == "login" || email.isNotBlank()),
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SlBrand),
        ) {
            if (state.loading) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(if (mode == "login") "登录" else "注册", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            buildAnnotatedString {
                append(if (mode == "login") "登录即代表你同意 " else "注册即表示你同意 ")
                withLink(
                    LinkAnnotation.Url(com.starlr.app.data.StarlrRepository.appAuthUrl(container.repository.serverConfig.current).removeSuffix("/app-auth") + "/terms", TextLinkStyles(SpanStyle(color = SlBrand, fontWeight = FontWeight.Bold)))
                ) { append("《用户协议》") }
                append(" 和 ")
                withLink(
                    LinkAnnotation.Url(com.starlr.app.data.StarlrRepository.appAuthUrl(container.repository.serverConfig.current).removeSuffix("/app-auth") + "/privacy", TextLinkStyles(SpanStyle(color = SlBrand, fontWeight = FontWeight.Bold)))
                ) { append("《隐私政策》") }
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(14.dp))
        TextButton(onClick = { mode = if (mode == "login") "register" else "login" }) {
            Text(
                if (mode == "login") "还没有账号？注册一个" else "已有账号？去登录",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
            )
        }
        Spacer(Modifier.height(40.dp))
    }
}
