package com.example.paperlessmeeting.domain.model

data class DeviceHeartbeat(
    val device_id: String, // Android ID or Mac Address hash
    val name: String,
    val model: String,
    val os_version: String,
    val app_version: String,
    val battery_level: Int,
    val is_charging: Boolean,
    val storage_total: Long,
    val storage_available: Long,
    val mac_address: String,
    val ip_address: String?,
    val status: String
)

data class DeviceResponse(
    val device_id: String,
    val name: String,
    val status: String,
    val last_active_at: String
)

data class AppUpdateCheck(
    val id: Int,
    val version_code: Int,
    val version_name: String,
    val release_notes: String,
    val is_force_update: Boolean,
    val download_url: String,
    val created_at: String
)

data class DeviceCommand(
    val id: Int,
    val device_id: String,
    val command_type: String,  // "update_app", "restart"
    val payload: String?,
    val status: String,
    val created_at: String,
    val acked_at: String?
)
