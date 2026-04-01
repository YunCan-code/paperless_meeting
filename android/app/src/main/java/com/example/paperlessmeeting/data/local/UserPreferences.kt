package com.example.paperlessmeeting.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class DeviceSettingsSnapshot(
        val themeMode: String? = null,
        val fontScaleLevel: Int? = null,
        val serverHost: String? = null
    )

    private val devicePrefs: SharedPreferences =
        context.getSharedPreferences("device_prefs", Context.MODE_PRIVATE)

    private val masterKey by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val securePrefs: SharedPreferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        EncryptedSharedPreferences.create(
            context,
            "user_prefs_encrypted",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    companion object {
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_DEPT = "user_dept"
        private const val KEY_USER_DISTRICT = "user_district"
        private const val KEY_USER_PHONE = "user_phone"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_TOKEN = "jwt_token"

        // Device-level settings (preserved on logout)
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_FONT_SCALE_LEVEL = "font_scale_level"
        private const val KEY_SERVER_HOST = "server_host"
        private const val KEY_LAST_USER_ID_HINT = "last_user_id_hint"
        private const val KEY_CHECKIN_HINT_SEEN_PREFIX = "checkin_hint_seen"
    }

    fun saveUserId(id: Int) {
        securePrefs.edit().putInt(KEY_USER_ID, id).apply()
        devicePrefs.edit().putInt(KEY_LAST_USER_ID_HINT, id).apply()
    }

    fun getUserId(): Int {
        return securePrefs.getInt(KEY_USER_ID, -1)
    }

    fun saveUserName(name: String) {
        securePrefs.edit().putString(KEY_USER_NAME, name).apply()
    }

    fun getUserName(): String? {
        return securePrefs.getString(KEY_USER_NAME, null)
    }

    fun saveUserDept(dept: String) {
        securePrefs.edit().putString(KEY_USER_DEPT, dept).apply()
    }

    fun getUserDept(): String? {
        return securePrefs.getString(KEY_USER_DEPT, null)
    }

    fun saveUserDistrict(district: String) {
        securePrefs.edit().putString(KEY_USER_DISTRICT, district).apply()
    }

    fun getUserDistrict(): String? {
        return securePrefs.getString(KEY_USER_DISTRICT, null)
    }

    fun saveUserPhone(phone: String) {
        securePrefs.edit().putString(KEY_USER_PHONE, phone).apply()
    }

    fun getUserPhone(): String? {
        return securePrefs.getString(KEY_USER_PHONE, null)
    }

    fun saveUserEmail(email: String) {
        securePrefs.edit().putString(KEY_USER_EMAIL, email).apply()
    }

    fun getUserEmail(): String? {
        return securePrefs.getString(KEY_USER_EMAIL, null)
    }

    fun saveUserRole(role: String) {
        securePrefs.edit().putString(KEY_USER_ROLE, role).apply()
    }

    fun getUserRole(): String? {
        return securePrefs.getString(KEY_USER_ROLE, null)
    }

    fun saveToken(token: String) {
        securePrefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? {
        return securePrefs.getString(KEY_TOKEN, null)
    }

    // --- Device-level settings (theme, font, server) ---

    fun saveThemeMode(mode: String) {
        devicePrefs.edit().putString(KEY_THEME_MODE, mode).apply()
    }

    fun getThemeMode(): String {
        return devicePrefs.getString(KEY_THEME_MODE, "system") ?: "system"
    }

    fun saveFontScaleLevel(level: Int) {
        devicePrefs.edit().putInt(KEY_FONT_SCALE_LEVEL, level).apply()
    }

    fun getFontScaleLevel(): Int {
        return devicePrefs.getInt(KEY_FONT_SCALE_LEVEL, 1) // default: 标准
    }

    fun saveServerHost(host: String) {
        devicePrefs.edit().putString(KEY_SERVER_HOST, host).apply()
    }

    fun getServerHost(): String {
        val apiUrl = com.example.paperlessmeeting.BuildConfig.API_BASE_URL
        val defaultHost = apiUrl.removeSuffix("/api/").removeSuffix("/api").removeSuffix("/")
        return devicePrefs.getString(KEY_SERVER_HOST, defaultHost) ?: defaultHost
    }

    fun getCachedUserIdHint(): Int {
        return devicePrefs.getInt(KEY_LAST_USER_ID_HINT, -1)
    }

    private fun checkInHintKey(userId: Int, meetingId: Int): String {
        return "${KEY_CHECKIN_HINT_SEEN_PREFIX}_${userId}_$meetingId"
    }

    fun hasSeenCheckInHint(userId: Int, meetingId: Int): Boolean {
        if (userId <= 0 || meetingId <= 0) return true
        return devicePrefs.getBoolean(checkInHintKey(userId, meetingId), false)
    }

    fun markCheckInHintSeen(userId: Int, meetingId: Int) {
        if (userId <= 0 || meetingId <= 0) return
        devicePrefs.edit().putBoolean(checkInHintKey(userId, meetingId), true).apply()
    }

    fun getCachedDeviceSettingsSnapshot(): DeviceSettingsSnapshot {
        val fontScaleLevel = if (devicePrefs.contains(KEY_FONT_SCALE_LEVEL)) {
            devicePrefs.getInt(KEY_FONT_SCALE_LEVEL, 1)
        } else {
            null
        }

        return DeviceSettingsSnapshot(
            themeMode = devicePrefs.getString(KEY_THEME_MODE, null),
            fontScaleLevel = fontScaleLevel,
            serverHost = devicePrefs.getString(KEY_SERVER_HOST, null)
        )
    }

    fun hydrateLegacyDeviceSettingsIfNeeded(): DeviceSettingsSnapshot {
        val editor = devicePrefs.edit()
        var changed = false

        if (!devicePrefs.contains(KEY_THEME_MODE)) {
            securePrefs.getString(KEY_THEME_MODE, null)?.let {
                editor.putString(KEY_THEME_MODE, it)
                changed = true
            }
        }

        if (!devicePrefs.contains(KEY_FONT_SCALE_LEVEL) && securePrefs.contains(KEY_FONT_SCALE_LEVEL)) {
            editor.putInt(KEY_FONT_SCALE_LEVEL, securePrefs.getInt(KEY_FONT_SCALE_LEVEL, 1))
            changed = true
        }

        if (!devicePrefs.contains(KEY_SERVER_HOST)) {
            securePrefs.getString(KEY_SERVER_HOST, null)?.let {
                editor.putString(KEY_SERVER_HOST, it)
                changed = true
            }
        }

        if (changed) {
            editor.apply()
        }

        return getCachedDeviceSettingsSnapshot()
    }

    fun clear() {
        securePrefs.edit().clear().apply()
        devicePrefs.edit().remove(KEY_LAST_USER_ID_HINT).apply()
    }
}
