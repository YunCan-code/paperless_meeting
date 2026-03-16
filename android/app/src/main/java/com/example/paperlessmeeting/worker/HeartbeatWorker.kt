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
    private val userPreferences: UserPreferences,
    private val deviceCommandSyncManager: DeviceCommandSyncManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val context = applicationContext
            
            val heartbeat = HeartbeatPayloadFactory.build(context, userPreferences)

            Log.d("HeartbeatWorker", "Sending heartbeat: $heartbeat")
            deviceRepository.sendHeartbeat(heartbeat)
            deviceCommandSyncManager.syncPendingCommands(heartbeat.device_id)
            
            Result.success()
        } catch (e: Exception) {
            Log.e("HeartbeatWorker", "Error sending heartbeat", e)
            Result.retry()
        }
    }
    
}
