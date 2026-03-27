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
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context, 
        "user_prefs_encrypted", 
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

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
        private const val KEY_CHECKIN_HINT_SEEN_PREFIX = "checkin_hint_seen"
    }

    fun saveUserId(id: Int) {
        prefs.edit().putInt(KEY_USER_ID, id).apply()
    }

    fun getUserId(): Int {
        return prefs.getInt(KEY_USER_ID, -1)
    }

    fun saveUserName(name: String) {
        prefs.edit().putString(KEY_USER_NAME, name).apply()
    }

    fun getUserName(): String? {
        return prefs.getString(KEY_USER_NAME, null)
    }

    fun saveUserDept(dept: String) {
        prefs.edit().putString(KEY_USER_DEPT, dept).apply()
    }

    fun getUserDept(): String? {
        return prefs.getString(KEY_USER_DEPT, null)
    }

    fun saveUserDistrict(district: String) {
        prefs.edit().putString(KEY_USER_DISTRICT, district).apply()
    }

    fun getUserDistrict(): String? {
        return prefs.getString(KEY_USER_DISTRICT, null)
    }

    fun saveUserPhone(phone: String) {
        prefs.edit().putString(KEY_USER_PHONE, phone).apply()
    }

    fun getUserPhone(): String? {
        return prefs.getString(KEY_USER_PHONE, null)
    }

    fun saveUserEmail(email: String) {
        prefs.edit().putString(KEY_USER_EMAIL, email).apply()
    }

    fun getUserEmail(): String? {
        return prefs.getString(KEY_USER_EMAIL, null)
    }

    fun saveUserRole(role: String) {
        prefs.edit().putString(KEY_USER_ROLE, role).apply()
    }

    fun getUserRole(): String? {
        return prefs.getString(KEY_USER_ROLE, null)
    }

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }

    // --- Device-level settings (theme, font, server) ---

    fun saveThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
    }

    fun getThemeMode(): String {
        return prefs.getString(KEY_THEME_MODE, "system") ?: "system"
    }

    fun saveFontScaleLevel(level: Int) {
        prefs.edit().putInt(KEY_FONT_SCALE_LEVEL, level).apply()
    }

    fun getFontScaleLevel(): Int {
        return prefs.getInt(KEY_FONT_SCALE_LEVEL, 1) // default: 标准
    }

    fun saveServerHost(host: String) {
        prefs.edit().putString(KEY_SERVER_HOST, host).apply()
    }

    fun getServerHost(): String {
        val apiUrl = com.example.paperlessmeeting.BuildConfig.API_BASE_URL
        val defaultHost = apiUrl.removeSuffix("/api/").removeSuffix("/api").removeSuffix("/")
        return prefs.getString(KEY_SERVER_HOST, defaultHost) ?: defaultHost
    }

    private fun checkInHintKey(userId: Int, meetingId: Int): String {
        return "${KEY_CHECKIN_HINT_SEEN_PREFIX}_${userId}_$meetingId"
    }

    fun hasSeenCheckInHint(userId: Int, meetingId: Int): Boolean {
        if (userId <= 0 || meetingId <= 0) return true
        return prefs.getBoolean(checkInHintKey(userId, meetingId), false)
    }

    fun markCheckInHintSeen(userId: Int, meetingId: Int) {
        if (userId <= 0 || meetingId <= 0) return
        prefs.edit().putBoolean(checkInHintKey(userId, meetingId), true).apply()
    }

    fun clear() {
        // Backup device-level settings before clearing
        val themeMode = getThemeMode()
        val fontScaleLevel = getFontScaleLevel()
        val serverHost = getServerHost()

        prefs.edit().clear().apply()

        // Restore device-level settings
        saveThemeMode(themeMode)
        saveFontScaleLevel(fontScaleLevel)
        saveServerHost(serverHost)
    }
}
