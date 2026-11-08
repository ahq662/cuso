package com.cuso.voice.ui.screens.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cuso.voice.data.ApiClient
import com.cuso.voice.data.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val loggedIn: Boolean = false,
)

class AuthViewModel(app: Application) : AndroidViewModel(app) {
    private val api = ApiClient()
    private val session = SessionStore(app)

    private val _state = MutableStateFlow(AuthUiState(loggedIn = session.token != null))
    val state = _state.asStateFlow()

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _state.update { it.copy(error = "请输入用户名和密码") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val res = api.login(username, password)
                session.save(res.token, res.user)
                _state.update { it.copy(loading = false, loggedIn = true) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "登录失败") }
            }
        }
    }

    fun register(username: String, password: String, confirm: String, nickname: String) {
        if (username.length !in 3..20) {
            _state.update { it.copy(error = "用户名需为 3-20 位") }
            return
        }
        if (password.length < 6) {
            _state.update { it.copy(error = "密码至少 6 位") }
            return
        }
        if (password != confirm) {
            _state.update { it.copy(error = "两次输入的密码不一致") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val res = api.register(username, password, nickname.ifBlank { null })
                session.save(res.token, res.user)
                _state.update { it.copy(loading = false, loggedIn = true) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "注册失败") }
            }
        }
    }
}
