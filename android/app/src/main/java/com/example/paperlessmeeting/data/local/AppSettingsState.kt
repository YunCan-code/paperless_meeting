package com.example.paperlessmeeting.data.local

import com.example.paperlessmeeting.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Singleton
class AppSettingsState @Inject constructor(
    private val userPreferences: UserPreferences
) {
    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _fontScaleLevel = MutableStateFlow(userPreferences.getFontScaleLevel())
    val fontScaleLevel: StateFlow<Int> = _fontScaleLevel.asStateFlow()

    private val _serverHost = MutableStateFlow(userPreferences.getServerHost())
    val serverHost: StateFlow<String> = _serverHost.asStateFlow()

    private fun loadThemeMode(): ThemeMode {
        return when (userPreferences.getThemeMode()) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        val key = when (mode) {
            ThemeMode.SYSTEM -> "system"
            ThemeMode.LIGHT -> "light"
            ThemeMode.DARK -> "dark"
        }
        userPreferences.saveThemeMode(key)
        _themeMode.value = mode
    }

    fun setFontScaleLevel(level: Int) {
        val clamped = level.coerceIn(0, 3)
        userPreferences.saveFontScaleLevel(clamped)
        _fontScaleLevel.value = clamped
    }

    fun setServerHost(host: String) {
        userPreferences.saveServerHost(host)
        _serverHost.value = host
    }

    fun resetServerHost() {
        val defaultHost = getDefaultHost()
        userPreferences.saveServerHost(defaultHost)
        _serverHost.value = defaultHost
    }

    fun getApiBaseUrl(): String {
        val host = _serverHost.value.trimEnd('/')
        return "$host/api/"
    }

    fun getSocketBaseUrl(): String {
        return _serverHost.value.trimEnd('/')
    }

    fun getStaticBaseUrl(): String {
        val host = _serverHost.value.trimEnd('/')
        return "$host/static/"
    }

    companion object {
        private val FONT_SCALE_FACTORS = floatArrayOf(0.85f, 1.0f, 1.15f, 1.3f)

        fun fontScaleFactor(level: Int): Float {
            return FONT_SCALE_FACTORS.getOrElse(level.coerceIn(0, 3)) { 1.0f }
        }

        fun getDefaultHost(): String {
            // Extract host from BuildConfig.API_BASE_URL, removing /api/ suffix
            val apiUrl = BuildConfig.API_BASE_URL
            return apiUrl.removeSuffix("/api/").removeSuffix("/api").removeSuffix("/")
        }
    }
}
