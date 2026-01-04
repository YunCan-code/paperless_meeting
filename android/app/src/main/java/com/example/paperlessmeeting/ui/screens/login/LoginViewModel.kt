package com.example.paperlessmeeting.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paperlessmeeting.data.remote.ApiService
import com.example.paperlessmeeting.domain.model.LoginRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val apiService: ApiService,
    private val userPreferences: com.example.paperlessmeeting.data.local.UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun login(query: String) {
        if (query.isBlank()) {
            _uiState.value = LoginUiState.Error("请输入姓名或手机号")
            return
        }

        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            try {
                val response = apiService.login(LoginRequest(query))
                userPreferences.saveUserName(response.name)
                _uiState.value = LoginUiState.Success(response.name)
            } catch (e: Exception) {
                // Parse error message if possible
                val msg = if (e.message?.contains("300") == true) "存在重名，请使用手机号" 
                          else if (e.message?.contains("404") == true) "用户不存在"
                          else "登录失败: ${e.message}"
                _uiState.value = LoginUiState.Error(msg)
            }
        }
    }
}

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    data class Success(val userName: String) : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}
