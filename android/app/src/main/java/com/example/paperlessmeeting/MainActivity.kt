package com.example.paperlessmeeting

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.paperlessmeeting.ui.screens.home.HomeScreen
import com.example.paperlessmeeting.ui.theme.PaperlessMeetingTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var userPreferences: com.example.paperlessmeeting.data.local.UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PaperlessMeetingTheme {
                AppRoot(userPreferences)
            }
        }
        
        // Trigger immediate heartbeat when app opens to update status
        val authRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.paperlessmeeting.worker.HeartbeatWorker>()
            .build()
        androidx.work.WorkManager.getInstance(this).enqueue(authRequest)
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
