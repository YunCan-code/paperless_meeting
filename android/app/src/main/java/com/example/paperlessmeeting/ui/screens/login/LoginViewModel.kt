package com.example.paperlessmeeting.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paperlessmeeting.data.local.AppSettingsState
import com.example.paperlessmeeting.data.repository.MeetingRepository
import com.example.paperlessmeeting.domain.model.LoginRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginPosterConfig(
    val posterUrl: String? = null,
    val posterVersion: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: MeetingRepository,
    private val userPreferences: com.example.paperlessmeeting.data.local.UserPreferences,
    private val appSettingsState: AppSettingsState
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState = _uiState.asStateFlow()

    private val _posterConfig = MutableStateFlow(LoginPosterConfig())
    val posterConfig = _posterConfig.asStateFlow()

    init {
        loadLoginPoster()
    }

    private fun loadLoginPoster() {
        viewModelScope.launch {
            try {
                val settings = repository.getSettings()
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
                val response = repository.login(LoginRequest(query))
                userPreferences.saveUserName(response.name)
                userPreferences.saveUserId(response.user_id)
                userPreferences.saveToken(response.token)
                response.role?.let { userPreferences.saveUserRole(it) }
                response.department?.let { userPreferences.saveUserDept(it) }
                response.district?.let { userPreferences.saveUserDistrict(it) }
                response.phone?.let { userPreferences.saveUserPhone(it) }
                response.email?.let { userPreferences.saveUserEmail(it) }
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
