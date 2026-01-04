package com.example.paperlessmeeting.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paperlessmeeting.data.local.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val userName: String = "Guest",
    val userRole: String = "参会人员", // Mock
    val userDept: String = "技术部", // Mock
    val cacheSize: String = "12.5 MB", // Mock initial
    val versionName: String = "v1.0.0",
    val isLoading: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadUserProfile()
    }

    private fun loadUserProfile() {
        val name = userPreferences.getUserName() ?: "未知用户"
        _uiState.value = _uiState.value.copy(userName = name)
        
        // Mock calculating cache size
        viewModelScope.launch {
            // In real app, use context.cacheDir.walkTopDown().sumOf { it.length() }
            delay(500) 
            _uiState.value = _uiState.value.copy(cacheSize = "24.8 MB")
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            delay(1000) // Simulate generic work
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                cacheSize = "0 B"
            )
        }
    }

    fun logout() {
        userPreferences.clear()
        // Navigation side-effect should be handled by UI observing a OneTimeEvent or simply callback
    }
}
