package com.example.paperlessmeeting

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.paperlessmeeting.data.local.AppSettingsState
import com.example.paperlessmeeting.data.local.ThemeMode
import com.example.paperlessmeeting.ui.screens.home.HomeScreen
import com.example.paperlessmeeting.ui.theme.PaperlessMeetingTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.paperlessmeeting.worker.HeartbeatWorker
import com.example.paperlessmeeting.worker.OfflineReportWorker
import java.util.concurrent.TimeUnit

import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var userPreferences: com.example.paperlessmeeting.data.local.UserPreferences
    @Inject lateinit var appSettingsState: AppSettingsState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by appSettingsState.themeMode.collectAsState()
            val fontScaleLevel by appSettingsState.fontScaleLevel.collectAsState()

            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            PaperlessMeetingTheme(
                darkTheme = darkTheme,
                fontScaleFactor = AppSettingsState.fontScaleFactor(fontScaleLevel)
            ) {
                AppRoot(userPreferences)
            }
        }
        
        // Trigger immediate heartbeat when app opens to update status
        val authRequest = OneTimeWorkRequestBuilder<HeartbeatWorker>()
            .build()
        WorkManager.getInstance(this).enqueue(authRequest)
    }

    override fun onStart() {
        super.onStart()
        WorkManager.getInstance(this).cancelUniqueWork(OFFLINE_REPORT_WORK_NAME)
    }

    override fun onStop() {
        super.onStop()
        scheduleOfflineReport()
    }

    private fun scheduleOfflineReport() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<OfflineReportWorker>()
            .setInitialDelay(20, TimeUnit.SECONDS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniqueWork(
            OFFLINE_REPORT_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    companion object {
        private const val OFFLINE_REPORT_WORK_NAME = "OfflineReportWork"
    }
}

@Composable
fun AppRoot(userPreferences: com.example.paperlessmeeting.data.local.UserPreferences) {
    // 每次启动都要求登录（公共会议室平板场景）
    var isLoggedIn by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (isLoggedIn) {
            com.example.paperlessmeeting.ui.screens.MainScreen(
                onLogout = { isLoggedIn = false }
            )
        } else {
            com.example.paperlessmeeting.ui.screens.login.LoginScreen(
                onLoginSuccess = {
                    isLoggedIn = true
                }
            )
        }
    }
}
