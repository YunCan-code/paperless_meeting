package com.example.paperlessmeeting.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paperlessmeeting.data.local.AppSettingsState
import com.example.paperlessmeeting.data.repository.MeetingRepository
import com.example.paperlessmeeting.domain.model.LoginRequest
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class LoginPosterConfig(
    val posterUrl: String? = null,
    val posterVersion: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: Lazy<MeetingRepository>,
    private val userPreferences: Lazy<com.example.paperlessmeeting.data.local.UserPreferences>,
    private val appSettingsState: AppSettingsState
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState = _uiState.asStateFlow()

    private val _posterConfig = MutableStateFlow(LoginPosterConfig())
    val posterConfig = _posterConfig.asStateFlow()

    private var isPosterLoadRequested = false

    fun ensurePosterLoaded() {
        if (isPosterLoadRequested) return
        isPosterLoadRequested = true
        loadLoginPoster()
    }

    private fun loadLoginPoster() {
        viewModelScope.launch {
            try {
                val settings = withContext(Dispatchers.IO) {
                    repository.get().getSettings()
                }
                val rawUrl = settings["android_login_poster_url"]?.trim().orEmpty()
                val resolvedUrl = when {
                    rawUrl.isBlank() -> null
                    rawUrl.startsWith("http://") || rawUrl.startsWith("https://") -> rawUrl
                    rawUrl.startsWith("/") -> "${appSettingsState.getSocketBaseUrl().trimEnd('/')}$rawUrl"
                    else -> rawUrl
                }
                _posterConfig.value = LoginPosterConfig(
                    posterUrl = resolvedUrl,
                    posterVersion = settings["android_login_poster_version"]?.trim()?.takeIf { it.isNotEmpty() }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _posterConfig.value = LoginPosterConfig()
            }
        }
    }

    fun login(query: String) {
        if (query.isBlank()) {
            _uiState.value = LoginUiState.Error("请输入姓名或手机号")
            return
        }

        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            try {
                val response = withContext(Dispatchers.IO) {
                    repository.get().login(LoginRequest(query))
                }
                withContext(Dispatchers.IO) {
                    val prefs = userPreferences.get()
                    prefs.saveUserName(response.name)
                    prefs.saveUserId(response.user_id)
                    prefs.saveToken(response.token)
                    response.role?.let { prefs.saveUserRole(it) }
                    response.department?.let { prefs.saveUserDept(it) }
                    response.district?.let { prefs.saveUserDistrict(it) }
                    response.phone?.let { prefs.saveUserPhone(it) }
                    response.email?.let { prefs.saveUserEmail(it) }
                }
                _uiState.value = LoginUiState.Success(response.name)
            } catch (e: Exception) {
                val msg = when {
                    e.message?.contains("300") == true -> "存在重名，请使用手机号"
                    e.message?.contains("404") == true -> "用户不存在"
                    else -> "登录失败: ${e.message}"
                }
                _uiState.value = LoginUiState.Error(msg)
            }
        }
    }

    fun resetState() {
        _uiState.value = LoginUiState.Idle
    }
}

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    data class Success(val userName: String) : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}
