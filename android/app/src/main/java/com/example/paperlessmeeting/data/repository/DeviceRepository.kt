package com.example.paperlessmeeting.data.repository

import com.example.paperlessmeeting.data.remote.ApiService
import com.example.paperlessmeeting.data.local.AppSettingsState
import com.example.paperlessmeeting.domain.model.AppUpdateCheck
import com.example.paperlessmeeting.domain.model.DeviceHeartbeat
import com.example.paperlessmeeting.domain.model.DeviceOfflineReport
import com.example.paperlessmeeting.domain.model.DeviceResponse
import javax.inject.Inject

class DeviceRepository @Inject constructor(
    private val apiService: ApiService,
    private val appSettingsState: AppSettingsState
) {
    suspend fun sendHeartbeat(heartbeat: DeviceHeartbeat): Result<DeviceResponse> {
        return try {
            val response = apiService.deviceHeartbeat(heartbeat)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkAppUpdate(): Result<AppUpdateCheck?> {
        return try {
            val response = apiService.checkAppUpdate()?.let(::resolveAppUpdate)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendOfflineReport(deviceId: String): Result<Unit> {
        return try {
            apiService.deviceOffline(DeviceOfflineReport(device_id = deviceId))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCommands(deviceId: String): Result<List<com.example.paperlessmeeting.domain.model.DeviceCommand>> {
        return try {
            val response = apiService.getDeviceCommands(deviceId)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun ackCommand(commandId: Int): Result<Boolean> {
        return try {
            apiService.ackCommand(commandId)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadApk(url: String): Result<okhttp3.ResponseBody> {
        return try {
            val response = apiService.downloadApk(resolveDownloadUrl(url))
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun resolveAppUpdate(update: AppUpdateCheck): AppUpdateCheck {
        return update.copy(download_url = resolveDownloadUrl(update.download_url))
    }

    private fun resolveDownloadUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }

        val host = appSettingsState.serverHost.value.trim().trimEnd('/')
        if (host.isEmpty()) {
            return trimmed
        }

        return if (trimmed.startsWith("/")) {
            host + trimmed
        } else {
            "$host/$trimmed"
        }
    }
}
