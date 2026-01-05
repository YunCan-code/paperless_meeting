package com.example.paperlessmeeting.data.repository

import com.example.paperlessmeeting.data.remote.ApiService
import com.example.paperlessmeeting.domain.model.AppUpdateCheck
import com.example.paperlessmeeting.domain.model.DeviceHeartbeat
import com.example.paperlessmeeting.domain.model.DeviceResponse
import javax.inject.Inject

class DeviceRepository @Inject constructor(
    private val apiService: ApiService
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
            val response = apiService.checkAppUpdate()
            Result.success(response)
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
            val response = apiService.downloadApk(url)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
