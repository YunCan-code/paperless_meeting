package com.example.paperlessmeeting.ui.screens.settings

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.paperlessmeeting.data.local.AppSettingsState
import com.example.paperlessmeeting.data.local.ReadingProgressManager
import com.example.paperlessmeeting.data.local.ThemeMode
import com.example.paperlessmeeting.data.local.UserPreferences
import com.example.paperlessmeeting.data.remote.ApiService
import com.example.paperlessmeeting.domain.model.AppUpdateCheck
import com.example.paperlessmeeting.domain.model.ChangePasswordRequest
import com.example.paperlessmeeting.worker.HeartbeatPayloadFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class DeviceInfo(
    val deviceId: String = "",
    val model: String = "",
    val osVersion: String = "",
    val appVersion: String = "",
    val ipAddress: String = "",
    val macAddress: String = "",
    val batteryLevel: Int = -1,
    val isCharging: Boolean = false,
    val storageTotalMB: Long = 0,
    val storageAvailableMB: Long = 0
)

data class SettingsUiState(
    val userName: String = "\u8bbf\u5ba2",
    val userRole: String = "\u53c2\u4f1a\u4eba\u5458",
    val userDept: String = "\u6280\u672f\u90e8",
    val userDistrict: String = "",
    val userPhone: String = "",
    val userEmail: String = "",
    val cacheSize: String = "\u8ba1\u7b97\u4e2d...",
    val versionName: String = "v1.0.0",
    val isLoading: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontScaleLevel: Int = 1,
    val deviceInfo: DeviceInfo = DeviceInfo(),
    val serverHost: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val apiService: ApiService,
    private val readingProgressManager: ReadingProgressManager,
    private val deviceRepository: com.example.paperlessmeeting.data.repository.DeviceRepository,
    private val appSettingsState: AppSettingsState,
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadUserProfile()
        calculateCacheSize()
        loadVersionName()
        loadAppSettings()
        loadDeviceInfo()
    }

    private fun loadVersionName() {
        try {
            val packageInfo = getApplication<Application>().packageManager.getPackageInfo(
                getApplication<Application>().packageName,
                0
            )
            _uiState.value = _uiState.value.copy(versionName = "v${packageInfo.versionName}")
        } catch (_: Exception) {
        }
    }

    private fun loadUserProfile() {
        val name = userPreferences.getUserName() ?: "\u672a\u77e5\u7528\u6237"
        val dept = userPreferences.getUserDept() ?: ""
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

        val userId = userPreferences.getUserId()
        if (userId != -1) {
            viewModelScope.launch {
                try {
                    val user = apiService.getUser(userId)
                    val serverName = user["name"]?.toString() ?: name
                    val serverDept = user["department"]?.toString() ?: ""
                    val serverDistrict = user["district"]?.toString() ?: ""
                    val serverPhone = user["phone"]?.toString() ?: ""
                    val serverEmail = user["email"]?.toString() ?: ""
                    val serverRole = user["position"]?.toString() ?: ""

                    userPreferences.saveUserName(serverName)
                    if (serverDept.isNotEmpty()) userPreferences.saveUserDept(serverDept)
                    if (serverDistrict.isNotEmpty()) userPreferences.saveUserDistrict(serverDistrict)
                    if (serverPhone.isNotEmpty()) userPreferences.saveUserPhone(serverPhone)
                    if (serverEmail.isNotEmpty()) userPreferences.saveUserEmail(serverEmail)
                    if (serverRole.isNotEmpty()) userPreferences.saveUserRole(serverRole)

                    _uiState.value = _uiState.value.copy(
                        userName = serverName,
                        userDept = serverDept,
                        userDistrict = serverDistrict,
                        userPhone = serverPhone,
                        userEmail = serverEmail
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun loadAppSettings() {
        _uiState.value = _uiState.value.copy(
            themeMode = appSettingsState.themeMode.value,
            fontScaleLevel = appSettingsState.fontScaleLevel.value,
            serverHost = appSettingsState.serverHost.value
        )
    }

    fun setThemeMode(mode: ThemeMode) {
        appSettingsState.setThemeMode(mode)
        _uiState.value = _uiState.value.copy(themeMode = mode)
    }

    fun setFontScaleLevel(level: Int) {
        appSettingsState.setFontScaleLevel(level)
        _uiState.value = _uiState.value.copy(fontScaleLevel = level)
    }

    fun loadDeviceInfo() {
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) {
                val heartbeat = HeartbeatPayloadFactory.build(
                    getApplication<Application>(),
                    userPreferences
                )
                DeviceInfo(
                    deviceId = heartbeat.device_id,
                    model = heartbeat.model,
                    osVersion = heartbeat.os_version,
                    appVersion = heartbeat.app_version_code?.let {
                        "${heartbeat.app_version} (Build $it)"
                    } ?: heartbeat.app_version,
                    ipAddress = heartbeat.ip_address ?: "",
                    macAddress = heartbeat.mac_address,
                    batteryLevel = heartbeat.battery_level,
                    isCharging = heartbeat.is_charging,
                    storageTotalMB = heartbeat.storage_total,
                    storageAvailableMB = heartbeat.storage_available
                )
            }
            _uiState.value = _uiState.value.copy(deviceInfo = info)
        }
    }

    fun copyDeviceInfoToClipboard() {
        val info = _uiState.value.deviceInfo
        val text = buildString {
            appendLine("\u8bbe\u5907ID: ${info.deviceId}")
            appendLine("\u578b\u53f7: ${info.model}")
            appendLine("\u7cfb\u7edf: ${info.osVersion}")
            appendLine("\u5e94\u7528\u7248\u672c: ${info.appVersion}")
            appendLine("IP: ${info.ipAddress}")
            appendLine("MAC: ${info.macAddress}")
            appendLine("\u7535\u6c60: ${info.batteryLevel}%${if (info.isCharging) " (\u5145\u7535\u4e2d)" else ""}")
            appendLine("\u5b58\u50a8: ${info.storageAvailableMB}MB / ${info.storageTotalMB}MB")
        }
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("\u8bbe\u5907\u4fe1\u606f", text))
    }

    fun updateServerHost(host: String) {
        appSettingsState.setServerHost(host)
        _uiState.value = _uiState.value.copy(serverHost = host)
    }

    fun resetServerHost() {
        appSettingsState.resetServerHost()
        _uiState.value = _uiState.value.copy(serverHost = appSettingsState.serverHost.value)
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
                val cacheDir = getApplication<Application>().cacheDir
                deleteFolder(cacheDir)
            }

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
        viewModelScope.launch {
            readingProgressManager.clearAll()
        }
        userPreferences.clear()
    }

    fun changePassword(
        oldPwd: String,
        newPwd: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val userId = userPreferences.getUserId()
                if (userId == -1) {
                    throw Exception("\u65e0\u6cd5\u83b7\u53d6\u7528\u6237\u4fe1\u606f")
                }

                apiService.changePassword(
                    ChangePasswordRequest(
                        user_id = userId,
                        old_password = oldPwd,
                        new_password = newPwd
                    )
                )
                _uiState.value = _uiState.value.copy(isLoading = false)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                onError(e.message ?: "\u4fee\u6539\u5931\u8d25")
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

            try {
                val userId = userPreferences.getUserId()
                if (userId != -1) {
                    apiService.updateUserProfile(
                        userId,
                        com.example.paperlessmeeting.domain.model.UserProfileUpdate(
                            department = dept.ifBlank { null },
                            district = district.ifBlank { null },
                            phone = phone.ifBlank { null },
                            email = email.ifBlank { null }
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

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

    fun checkForUpdate(
        onNoUpdate: () -> Unit,
        onUpdateAvailable: (AppUpdateCheck) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val result = deviceRepository.checkAppUpdate()
                _uiState.value = _uiState.value.copy(isLoading = false)

                if (result.isFailure) {
                    onError("\u68c0\u67e5\u66f4\u65b0\u5931\u8d25: ${result.exceptionOrNull()?.message}")
                    return@launch
                }

                val remoteUpdate = result.getOrNull()
                if (remoteUpdate == null) {
                    onNoUpdate()
                    return@launch
                }

                val currentVersionCode = getCurrentVersionCode()
                if (currentVersionCode != null && remoteUpdate.version_code > currentVersionCode) {
                    onUpdateAvailable(remoteUpdate)
                } else {
                    onNoUpdate()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                onError("\u68c0\u67e5\u66f4\u65b0\u5931\u8d25: ${e.message}")
            }
        }
    }

    fun triggerAppUpdate(update: AppUpdateCheck) {
        viewModelScope.launch {
            try {
                val updateRequest =
                    androidx.work.OneTimeWorkRequestBuilder<com.example.paperlessmeeting.worker.UpdateWorker>()
                        .setInputData(
                            androidx.work.Data.Builder()
                                .putString(
                                    com.example.paperlessmeeting.worker.UpdateWorker.KEY_DOWNLOAD_URL,
                                    update.download_url
                                )
                                .putInt(com.example.paperlessmeeting.worker.UpdateWorker.KEY_COMMAND_ID, -1)
                                .build()
                        )
                        .build()
                androidx.work.WorkManager.getInstance(getApplication()).enqueueUniqueWork(
                    "manual-app-update",
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    updateRequest
                )
            } catch (_: Exception) {
            }
        }
    }

    private fun getCurrentVersionCode(): Int? {
        return try {
            val packageInfo = getApplication<Application>().packageManager.getPackageInfo(
                getApplication<Application>().packageName,
                0
            )
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        } catch (_: Exception) {
            null
        }
    }
}
