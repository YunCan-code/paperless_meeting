package com.example.paperlessmeeting.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.paperlessmeeting.data.local.UserPreferences
import com.example.paperlessmeeting.data.repository.DeviceRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class HeartbeatWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val deviceRepository: DeviceRepository,
    private val userPreferences: UserPreferences
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val context = applicationContext
            
            val heartbeat = HeartbeatPayloadFactory.build(context, userPreferences)

            Log.d("HeartbeatWorker", "Sending heartbeat: $heartbeat")
            deviceRepository.sendHeartbeat(heartbeat)
            
            // Poll for pending commands
            val commandsResult = deviceRepository.getCommands(heartbeat.device_id)
            if (commandsResult.isSuccess) {
                val commands = commandsResult.getOrNull() ?: emptyList()
                for (cmd in commands) {
                    Log.d("HeartbeatWorker", "Received command: ${cmd.command_type}")
                    
                    if (cmd.command_type == "update_app") {
                        // Get latest update info and trigger UpdateWorker
                        val updateResult = deviceRepository.checkAppUpdate()
                        if (updateResult.isSuccess && updateResult.getOrNull() != null) {
                            val update = updateResult.getOrNull()!!
                            val updateRequest = androidx.work.OneTimeWorkRequestBuilder<UpdateWorker>()
                                .setInputData(
                                    androidx.work.Data.Builder()
                                        .putString(UpdateWorker.KEY_DOWNLOAD_URL, update.download_url)
                                        .putInt(UpdateWorker.KEY_COMMAND_ID, cmd.id)
                                        .build()
                                )
                                .build()
                            androidx.work.WorkManager.getInstance(applicationContext).enqueue(updateRequest)
                        }
                    }
                    // Other command types (restart, etc.) can be handled here
                }
            }
            
            Result.success()
        } catch (e: Exception) {
            Log.e("HeartbeatWorker", "Error sending heartbeat", e)
            Result.retry()
        }
    }
    
}
