package com.example.paperlessmeeting.worker

import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.paperlessmeeting.data.local.UserPreferences
import com.example.paperlessmeeting.data.repository.DeviceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Singleton
class ForegroundHeartbeatManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceRepository: DeviceRepository,
    private val userPreferences: UserPreferences,
    private val deviceCommandSyncManager: DeviceCommandSyncManager
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var heartbeatJob: Job? = null

    fun register() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        startLoop()
    }

    override fun onStop(owner: LifecycleOwner) {
        stopLoop()
    }

    private fun startLoop() {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = scope.launch {
            while (isActive) {
                sendHeartbeatOnce()
                delay(FOREGROUND_HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    private fun stopLoop() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private suspend fun sendHeartbeatOnce() {
        val userId = userPreferences.getUserId()
        if (userId <= 0) return
        try {
            val heartbeat = HeartbeatPayloadFactory.build(context, userPreferences)
            deviceRepository.sendHeartbeat(heartbeat)
            deviceCommandSyncManager.syncPendingCommands(heartbeat.device_id)
        } catch (e: Exception) {
            Log.e("ForegroundHeartbeat", "Error sending heartbeat", e)
        }
    }

    companion object {
        private const val FOREGROUND_HEARTBEAT_INTERVAL_MS = 2 * 60 * 1000L
    }
}
