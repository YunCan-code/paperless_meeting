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
    val userName: String = "Guest",
    val userRole: String = "参会人员",
    val userDept: String = "技术部",
    val userDistrict: String = "",
    val userPhone: String = "",
    val userEmail: String = "",
    val cacheSize: String = "计算中...",
    val versionName: String = "v1.0.0",
    val isLoading: Boolean = false,
    // Phase 2: Appearance
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontScaleLevel: Int = 1,
    // Phase 3: Device info
    val deviceInfo: DeviceInfo = DeviceInfo(),
    // Phase 4: Server address
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
            val pInfo = getApplication<Application>().packageManager.getPackageInfo(
                getApplication<Application>().packageName, 0
            )
            _uiState.value = _uiState.value.copy(versionName = "v${pInfo.versionName}")
        } catch (e: Exception) { }
    }

    private fun loadUserProfile() {
        // 先用本地缓存快速展示
        val name = userPreferences.getUserName() ?: "未知用户"
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

        // 从服务器拉取最新数据
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

                    // 更新本地缓存
                    userPreferences.saveUserName(serverName)
                    if (serverDept.isNotEmpty()) userPreferences.saveUserDept(serverDept)
                    if (serverDistrict.isNotEmpty()) userPreferences.saveUserDistrict(serverDistrict)
                    if (serverPhone.isNotEmpty()) userPreferences.saveUserPhone(serverPhone)
                    if (serverEmail.isNotEmpty()) userPreferences.saveUserEmail(serverEmail)
                    if (serverRole.isNotEmpty()) userPreferences.saveUserRole(serverRole)

                    // 更新 UI
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

    // --- Phase 2: Appearance ---

    fun setThemeMode(mode: ThemeMode) {
        appSettingsState.setThemeMode(mode)
        _uiState.value = _uiState.value.copy(themeMode = mode)
    }

    fun setFontScaleLevel(level: Int) {
        appSettingsState.setFontScaleLevel(level)
        _uiState.value = _uiState.value.copy(fontScaleLevel = level)
    }

    // --- Phase 3: Device info ---

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
                    appVersion = heartbeat.app_version,
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
            appendLine("设备ID: ${info.deviceId}")
            appendLine("型号: ${info.model}")
            appendLine("系统: ${info.osVersion}")
            appendLine("应用版本: ${info.appVersion}")
            appendLine("IP: ${info.ipAddress}")
            appendLine("MAC: ${info.macAddress}")
            appendLine("电池: ${info.batteryLevel}%${if (info.isCharging) " (充电中)" else ""}")
            appendLine("存储: ${info.storageAvailableMB}MB / ${info.storageTotalMB}MB")
        }
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("设备信息", text))
    }

    // --- Phase 4: Server address ---

    fun updateServerHost(host: String) {
        appSettingsState.setServerHost(host)
        _uiState.value = _uiState.value.copy(serverHost = host)
    }

    fun resetServerHost() {
        appSettingsState.resetServerHost()
        _uiState.value = _uiState.value.copy(serverHost = appSettingsState.serverHost.value)
    }

    // --- Existing functionality ---

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

    fun changePassword(oldPwd: String, newPwd: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val userId = userPreferences.getUserId()
                if (userId == -1) {
                     throw Exception("无法获取用户信息")
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
        onUpdateAvailable: (versionName: String, releaseNotes: String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val result = deviceRepository.checkAppUpdate()
                _uiState.value = _uiState.value.copy(isLoading = false)

                if (result.isFailure) {
                    onError("检查更新失败: ${result.exceptionOrNull()?.message}")
                    return@launch
                }

                val remoteUpdate = result.getOrNull()
                if (remoteUpdate == null) {
                    onNoUpdate()
                    return@launch
                }

                val pInfo = getApplication<Application>().packageManager.getPackageInfo(
                    getApplication<Application>().packageName, 0
                )
                val currentVersionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    pInfo.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    pInfo.versionCode
                }

                if (remoteUpdate.version_code > currentVersionCode) {
                    onUpdateAvailable(remoteUpdate.version_name, remoteUpdate.release_notes)
                } else {
                    onNoUpdate()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                onError("检查更新失败: ${e.message}")
            }
        }
    }

    fun triggerAppUpdate() {
        viewModelScope.launch {
            try {
                val result = deviceRepository.checkAppUpdate()
                if (result.isSuccess && result.getOrNull() != null) {
                    val update = result.getOrNull()!!
                    val updateRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.paperlessmeeting.worker.UpdateWorker>()
                        .setInputData(
                            androidx.work.Data.Builder()
                                .putString(com.example.paperlessmeeting.worker.UpdateWorker.KEY_DOWNLOAD_URL, update.download_url)
                                .putInt(com.example.paperlessmeeting.worker.UpdateWorker.KEY_COMMAND_ID, -1)
                                .build()
                        )
                        .build()
                    androidx.work.WorkManager.getInstance(getApplication()).enqueue(updateRequest)
                }
            } catch (e: Exception) { }
        }
    }
}
