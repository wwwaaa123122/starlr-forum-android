package com.starlr.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.starlr.app.AppContainer
import com.starlr.app.data.Session
import com.starlr.app.data.StarlrRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 会话状态全局 VM：底部未读徽标 + 登录态。
 * 401 过期时 Repository 会清空会话，此 VM 负责发起初始加载与未读轮询。
 */
class SessionViewModel(private val repo: StarlrRepository) : ViewModel() {

    val session: StateFlow<Session> = repo.session

    init {
        refreshAll()
    }

    fun refreshAll() {
        viewModelScope.launch {
            if (repo.session.value.loggedIn) {
                repo.loadCurrentUser()
                repo.refreshUnread()
            }
        }
    }

    fun refreshUnread() {
        viewModelScope.launch { repo.refreshUnread() }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { SessionViewModel(container.repository) }
        }
    }
}
