package com.example.paperlessmeeting.worker

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import com.example.paperlessmeeting.BuildConfig
import com.example.paperlessmeeting.data.local.UserPreferences
import com.example.paperlessmeeting.domain.model.DeviceHeartbeat
import java.net.NetworkInterface
import java.util.Collections

object HeartbeatPayloadFactory {
    fun build(context: Context, userPreferences: UserPreferences): DeviceHeartbeat {
        val (batteryPct, isCharging) = getBatteryStatus(context)
        val (storageTotal, storageAvailable) = getStorageStatus()

        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val loginUserName = userPreferences.getUserName()?.trim().orEmpty()
        val currentUserId = userPreferences.getUserId().takeIf { it > 0 }
        val displayName = if (loginUserName.isNotBlank()) {
            loginUserName
        } else {
            "${Build.MANUFACTURER} ${Build.MODEL}"
        }

        return DeviceHeartbeat(
            device_id = androidId,
            user_id = currentUserId,
            name = displayName,
            model = Build.MODEL,
            os_version = "Android ${Build.VERSION.RELEASE}",
            app_version = BuildConfig.VERSION_NAME,
            battery_level = batteryPct,
            is_charging = isCharging,
            storage_total = storageTotal / (1024 * 1024), // MB
            storage_available = storageAvailable / (1024 * 1024), // MB
            mac_address = getMacAddress(),
            ip_address = getIpAddress(),
            status = "active"
        )
    }

    private fun getBatteryStatus(context: Context): Pair<Int, Boolean> {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) {
            (level * 100 / scale.toFloat()).toInt()
        } else {
            -1
        }
        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return batteryPct to isCharging
    }

    private fun getStorageStatus(): Pair<Long, Long> {
        val stat = StatFs(Environment.getDataDirectory().path)
        val bytesAvailable = stat.availableBlocksLong * stat.blockSizeLong
        val bytesTotal = stat.blockCountLong * stat.blockSizeLong
        return bytesTotal to bytesAvailable
    }

    private fun getMacAddress(): String {
        return try {
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
                res1.toString()
            }
            "02:00:00:00:00:00"
        } catch (_: Exception) {
            "02:00:00:00:00:00"
        }
    }

    private fun getIpAddress(): String {
        return try {
            val all = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (nif in all) {
                if (!nif.isUp || nif.isLoopback) continue
                val addrs = Collections.list(nif.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress ?: ""
                    }
                }
            }
            ""
        } catch (_: Exception) {
            ""
        }
    }
}
