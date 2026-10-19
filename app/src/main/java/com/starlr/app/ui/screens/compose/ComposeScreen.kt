package com.starlr.app.ui.screens.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.starlr.app.AppContainer
import com.starlr.app.data.Category
import com.starlr.app.data.CreatePostRequest
import com.starlr.app.data.CreateReplyRequest
import com.starlr.app.data.StarlrRepository
import com.starlr.app.ui.BuildConfigAware
import com.starlr.app.ui.components.UploadingIndicator
import com.starlr.app.ui.components.rememberImageUploadLauncher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ComposeState(
    val categories: List<Category> = emptyList(),
    val submitting: Boolean = false,
    val error: String? = null,
    val donePostId: String? = null,
)

/** 发帖 VM：分类加载 + 创建帖子/回复 */
class ComposeViewModel(private val repo: StarlrRepository) : ViewModel() {

    private val _state = MutableStateFlow(ComposeState())
    val state: StateFlow<ComposeState> = _state

    init {
        viewModelScope.launch {
            runCatching { repo.api.listCategories().categories }
                .onSuccess { cats ->
                    _state.value = _state.value.copy(
                        categories = cats.filter { it.postPolicy == "everyone" || it.postPolicy.isBlank() },
                    )
                }
        }
    }

    /** replyTo != null 时为回复模式 */
    fun submit(title: String, content: String, categorySlug: String?, tags: List<String>, replyTo: String?) {
        if (_state.value.submitting) return
        _state.value = _state.value.copy(submitting = true, error = null)
        viewModelScope.launch {
            if (replyTo != null) {
                runCatching { repo.api.createReply(replyTo, CreateReplyRequest(content.trim(), null)) }
                    .onSuccess {
                        _state.value = _state.value.copy(submitting = false, donePostId = replyTo)
                    }
                    .onFailure { e ->
                        _state.value = _state.value.copy(
                            submitting = false,
                            error = StarlrRepository.toApiError(e).message,
                        )
                    }
            } else {
                val category = _state.value.categories.firstOrNull { it.slug == categorySlug }
                    ?: _state.value.categories.firstOrNull()
                if (category == null) {
                    _state.value = _state.value.copy(submitting = false, error = "请选择板块")
                    return@launch
                }
                runCatching {
                    repo.api.createPost(
                        CreatePostRequest(
                            title = title.trim(),
                            content = content.trim(),
                            categoryId = category.id,
                            tags = tags,
                        ),
                    )
                }.onSuccess { res ->
                    _state.value = _state.value.copy(submitting = false, donePostId = res.id)
                }.onFailure { e ->
                    _state.value = _state.value.copy(
                        submitting = false,
                        error = StarlrRepository.toApiError(e).message,
                    )
                }
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ComposeViewModel(container.repository) as T
    }
}

private val TITLE_MAX = 120
private val TAGS_MAX = 5

/**
 * 发帖页（全屏）：标题 + 正文 + 板块选择 + 标签；replyTo 非空时为回复模式。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComposeScreen(
    container: AppContainer,
    replyTo: String?,
    onBack: () -> Unit,
    onDone: (String?) -> Unit,
) {
    val vm: ComposeViewModel = viewModel(factory = ComposeViewModel.Factory(container))
    val state by vm.state.collectAsState()

    var title by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    var tagInput by rememberSaveable { mutableStateOf("") }
    var tags by rememberSaveable { mutableStateOf(listOf<String>()) }
    var categorySlug by rememberSaveable { mutableStateOf<String?>(null) }
    var uploadingImage by remember { mutableStateOf(false) }
    var uploadError by remember { mutableStateOf<String?>(null) }

    // 选图上传：成功后在正文末尾插入 Markdown 图片（与 web 端一致）
    val pickImage = rememberImageUploadLauncher(
        repo = container.repository,
        purpose = if (replyTo != null) "reply" else "post",
        maxSizeMB = 5,
        onError = { uploadError = it },
    ) { url ->
        content = content.trimEnd() + "\n\n![图片]($url)\n"  // 与 web 端一致：相对路径，渲染时拼 baseUrl
    }

    LaunchedEffect(state.donePostId) {
        state.donePostId?.let { onDone(it) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .imePadding(),
    ) {
        // 顶栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                if (replyTo != null) "回复" else "发帖",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { vm.submit(title, content, categorySlug, tags.toList(), replyTo) },
                enabled = !state.submitting && content.isNotBlank() && (replyTo != null || (title.isNotBlank() && categorySlug != null)),
                modifier = Modifier.padding(end = 12.dp),
                shape = RoundedCornerShape(999.dp),
            ) {
                if (state.submitting) {
                    CircularProgressIndicator(Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("发布", fontWeight = FontWeight.Bold)
                }
            }
        }

        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (replyTo == null) {
                // 标题
                Column {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { if (it.length <= TITLE_MAX) title = it },
                        placeholder = { Text("输入标题…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            cursorColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                    Text(
                        "${title.length}/$TITLE_MAX",
                        fontSize = 12.sp,
                        color = if (title.length >= TITLE_MAX) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                    )
                }

                // 板块分类：胶囊 chips 行（与应用整体极简风格一致，单选高亮）
                Column {
                    Text(
                        "板块",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.categories.forEach { cat ->
                            val selected = cat.slug == categorySlug
                            Text(
                                text = "${cat.icon.orEmpty()} ${cat.name}".trim(),
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                    .clickable { categorySlug = cat.slug }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }

            // 图片上传（发帖/回复均可）
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { pickImage() }, enabled = !state.submitting) {
                    Icon(
                        Icons.Outlined.AddPhotoAlternate,
                        contentDescription = "插入图片",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    "插入图片（≤5MB，JPG/PNG/GIF/WEBP）",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            uploadError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            // 正文
            OutlinedTextField(
                value = content,
                onValueChange = { if (it.length <= 50_000) content = it },
                placeholder = { Text(if (replyTo != null) "写下你的回复…" else "说点什么…（支持 Markdown）") },
                minLines = if (replyTo != null) 3 else 8,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
            )

            // 标签（仅发帖模式）
            if (replyTo == null) {
                Column {
                    OutlinedTextField(
                        value = tagInput,
                        onValueChange = { tagInput = it },
                        placeholder = { Text("添加标签（最多 $TAGS_MAX 个，回车确认）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            cursorColor = MaterialTheme.colorScheme.primary,
                        ),
                        trailingIcon = {
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    val t = tagInput.trim().trimStart('#')
                                    if (t.isNotBlank() && tags.size < TAGS_MAX && t !in tags) {
                                        tags = tags + t
                                    }
                                    tagInput = ""
                                },
                            ) { Text("添加") }
                        },
                    )
                    if (tags.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            tags.forEach { t ->
                                Text(
                                    "#$t ✕",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(999.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    // 点击移除由长按/后续迭代承担
                                )
                            }
                        }
                    }
                }
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
