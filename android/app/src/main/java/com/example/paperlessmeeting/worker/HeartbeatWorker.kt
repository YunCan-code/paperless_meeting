package com.example.paperlessmeeting.worker

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.paperlessmeeting.BuildConfig
import com.example.paperlessmeeting.data.repository.DeviceRepository
import com.example.paperlessmeeting.domain.model.DeviceHeartbeat
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.net.NetworkInterface
import java.util.*

@HiltWorker
class HeartbeatWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val deviceRepository: DeviceRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val context = applicationContext
            
            // Battery
            val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
                context.registerReceiver(null, ifilter)
            }
            val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = (level * 100 / scale.toFloat()).toInt()
            val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            // Storage
            val stat = StatFs(Environment.getDataDirectory().path)
            val bytesAvailable = stat.availableBlocksLong * stat.blockSizeLong
            val bytesTotal = stat.blockCountLong * stat.blockSizeLong
            
            // WiFi / MAC
            // Note: MAC address is restricted on newer Android, ususally returns 02:00:00:00:00:00
            // We use ANDROID_ID as device_id mostly.
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            
            val heartbeat = DeviceHeartbeat(
                device_id = androidId,
                name = "${Build.MANUFACTURER} ${Build.MODEL}",
                model = Build.MODEL,
                os_version = "Android ${Build.VERSION.RELEASE}",
                app_version = BuildConfig.VERSION_NAME,
                battery_level = batteryPct,
                is_charging = isCharging,
                storage_total = bytesTotal / (1024 * 1024), // MB
                storage_available = bytesAvailable / (1024 * 1024), // MB
                mac_address = getMacAddress(),
                ip_address = getIpAddress(),
                status = "active"
            )

            Log.d("HeartbeatWorker", "Sending heartbeat: $heartbeat")
            deviceRepository.sendHeartbeat(heartbeat)
            
            // Poll for pending commands
            val commandsResult = deviceRepository.getCommands(androidId)
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
    
    private fun getMacAddress(): String {
        try {
            val all = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (nif in all) {
                if (!nif.name.equals("wlan0", ignoreCase = true)) continue
                val macBytes = nif.hardwareAddress ?: return ""
                val res1 = StringBuilder()
                for (b in macBytes) {
                    res1.append(String.format("%02X:", b))
                }
                if (res1.isNotEmpty()) {
                    res1.deleteCharAt(res1.length - 1)
                }
                return res1.toString()
            }
        } catch (ex: Exception) { }
        return "02:00:00:00:00:00"
    }

    private fun getIpAddress(): String {
        try {
            val all = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (nif in all) {
                // Check widely for wlan or eth
                if (!nif.isUp || nif.isLoopback) continue
                
                val addrs = Collections.list(nif.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress ?: ""
                    }
                }
            }
        } catch (ex: Exception) { }
        return ""
    }
}
