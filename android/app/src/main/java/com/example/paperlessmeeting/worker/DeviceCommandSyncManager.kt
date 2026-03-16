package com.example.paperlessmeeting.worker

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.paperlessmeeting.data.repository.DeviceRepository
import com.example.paperlessmeeting.domain.model.DeviceCommand
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceCommandSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceRepository: DeviceRepository
) {

    suspend fun syncPendingCommands(deviceId: String) {
        val commandsResult = deviceRepository.getCommands(deviceId)
        if (commandsResult.isFailure) {
            Log.e("DeviceCommandSync", "Failed to fetch commands", commandsResult.exceptionOrNull())
            return
        }

        val commands = commandsResult.getOrNull().orEmpty()
        for (command in commands) {
            when (command.command_type) {
                "update_app" -> handleUpdateCommand(command)
            }
        }
    }

    private suspend fun handleUpdateCommand(command: DeviceCommand) {
        val updateResult = deviceRepository.checkAppUpdate()
        if (updateResult.isFailure) {
            Log.e("DeviceCommandSync", "Failed to fetch latest update", updateResult.exceptionOrNull())
            return
        }

        val update = updateResult.getOrNull() ?: run {
            deviceRepository.ackCommand(command.id)
            return
        }

        val currentVersionCode = getCurrentVersionCode()
        if (currentVersionCode != null && update.version_code <= currentVersionCode) {
            deviceRepository.ackCommand(command.id)
            return
        }

        val updateRequest = OneTimeWorkRequestBuilder<UpdateWorker>()
            .setInputData(
                Data.Builder()
                    .putString(UpdateWorker.KEY_DOWNLOAD_URL, update.download_url)
                    .putInt(UpdateWorker.KEY_COMMAND_ID, command.id)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "device-command-update-${command.id}",
            ExistingWorkPolicy.KEEP,
            updateRequest
        )
    }

    private fun getCurrentVersionCode(): Int? {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
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
