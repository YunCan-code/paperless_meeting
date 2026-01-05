package com.example.paperlessmeeting.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.paperlessmeeting.data.local.ReadingProgressManager
import com.example.paperlessmeeting.data.local.UserPreferences
import com.example.paperlessmeeting.data.remote.ApiService
import com.example.paperlessmeeting.domain.model.ChangePasswordRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class SettingsUiState(
    val userName: String = "Guest",
    val userRole: String = "参会人员",
    val userDept: String = "技术部",
    val userDistrict: String = "", // 区县
    val userPhone: String = "", // 联系方式
    val userEmail: String = "", // 邮箱
    val cacheSize: String = "计算中...",
    val versionName: String = "v1.0.0",
    val isLoading: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val apiService: ApiService,
    private val readingProgressManager: ReadingProgressManager,
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadUserProfile()
        calculateCacheSize()
    }

    private fun loadUserProfile() {
        val name = userPreferences.getUserName() ?: "未知用户"
        val dept = userPreferences.getUserDept() ?: "技术部"
        val district = userPreferences.getUserDistrict() ?: ""
        val phone = userPreferences.getUserPhone() ?: ""
        val email = userPreferences.getUserEmail() ?: ""
        
        _uiState.value = _uiState.value.copy(
            userName = name,
            userDept = dept,
            userDistrict = district,
            userPhone = phone,
            userEmail = email
        )
    }

    private fun calculateCacheSize() {
        viewModelScope.launch {
            val size = withContext(Dispatchers.IO) {
                val cacheDir = getApplication<Application>().cacheDir
                calculateFolderSize(cacheDir)
            }
            _uiState.value = _uiState.value.copy(cacheSize = formatFileSize(size))
        }
    }

    private fun calculateFolderSize(folder: File): Long {
        var size = 0L
        folder.listFiles()?.forEach { file ->
            size += if (file.isDirectory) {
                calculateFolderSize(file)
            } else {
                file.length()
            }
        }
        return size
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            withContext(Dispatchers.IO) {
                // 清除缓存文件
                val cacheDir = getApplication<Application>().cacheDir
                deleteFolder(cacheDir)
            }
            
            // 清空最近阅读记录
            readingProgressManager.clearAll()
            
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                cacheSize = "0 B"
            )
        }
    }

    private fun deleteFolder(folder: File) {
        folder.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                deleteFolder(file)
            }
            file.delete()
        }
    }

    fun logout() {
        userPreferences.clear()
        // Navigation side-effect should be handled by UI observing a OneTimeEvent or simply callback
    }

    fun changePassword(oldPwd: String, newPwd: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val userId = userPreferences.getUserId()
                if (userId == -1) { // Assuming -1 or similar is invalid
                     throw Exception("无法获取用户信息")
                }
                
                apiService.changePassword(
                    ChangePasswordRequest(
                        user_id = userId, // Need to implement getUserId in UserPreferences
                        old_password = oldPwd,
                        new_password = newPwd
                    )
                )
                _uiState.value = _uiState.value.copy(isLoading = false)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                onError(e.message ?: "修改失败")
            }
        }
    }

    fun updateProfile(
        dept: String,
        district: String,
        phone: String,
        email: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            // 模拟API调用延迟
            kotlinx.coroutines.delay(1000)
            
            // 保存到本地 Preferences
            withContext(Dispatchers.IO) {
                userPreferences.saveUserDept(dept)
                userPreferences.saveUserDistrict(district)
                userPreferences.saveUserPhone(phone)
                userPreferences.saveUserEmail(email)
            }
            
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                userDept = dept,
                userDistrict = district,
                userPhone = phone,
                userEmail = email
            )
            onSuccess()
        }
    }
}
