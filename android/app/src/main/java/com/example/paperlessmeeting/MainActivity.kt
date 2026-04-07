package com.example.paperlessmeeting

import android.content.res.Configuration
import android.os.Bundle
import android.view.Choreographer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ScreenRotationAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.paperlessmeeting.data.local.AppSettingsState
import com.example.paperlessmeeting.data.local.ThemeMode
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
import com.example.paperlessmeeting.worker.OfflineReportWorker
import java.util.concurrent.TimeUnit

import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var appSettingsState: AppSettingsState
    private var deferredStartupScheduled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        StartupTrace.mark("MainActivity.onCreate.start")
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
                AppRoot(
                    onExitApp = { finish() }
                )
            }
        }
        StartupTrace.mark("MainActivity.setContent.complete")
        scheduleDeferredStartupAfterFirstFrame()
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

    private fun scheduleDeferredStartupAfterFirstFrame() {
        if (deferredStartupScheduled) return
        deferredStartupScheduled = true
        window.decorView.post {
            Choreographer.getInstance().postFrameCallback {
                StartupTrace.mark("MainActivity.first_frame.committed")
                startDeferredStartupOnce()
            }
        }
    }

    private fun startDeferredStartupOnce() {
        StartupTrace.mark("MainActivity.deferred_startup.begin")
        (application as? PaperlessApp)?.ensureDeferredStartupTasksStarted()
    }

    companion object {
        private const val OFFLINE_REPORT_WORK_NAME = "OfflineReportWork"
    }
}

@Composable
fun AppRoot(
    onExitApp: () -> Unit = {}
) {
    var isLoggedIn by remember { mutableStateOf(false) }
    var allowPortraitContent by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val shouldShowPortraitOverlay = isPortrait && !(isLoggedIn && allowPortraitContent)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isLoggedIn) {
                com.example.paperlessmeeting.ui.screens.MainScreen(
                    onLogout = {
                        allowPortraitContent = false
                        isLoggedIn = false
                    },
                    onPortraitExemptionChanged = { allowPortraitContent = it },
                    onExitApp = onExitApp
                )
            } else {
                SideEffect {
                    allowPortraitContent = false
                }
                com.example.paperlessmeeting.ui.screens.login.LoginScreen(
                    onLoginSuccess = {
                        isLoggedIn = true
                    }
                )
            }

            if (shouldShowPortraitOverlay) {
                PortraitOrientationOverlay()
            }
        }
    }
}

@Composable
private fun PortraitOrientationOverlay() {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {}
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xF20B1120)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraLarge)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ScreenRotationAlt,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "请横屏使用",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "当前页面为横屏布局设计，请将设备旋转至横向后继续操作。阅读 PDF 时可保持竖屏。",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.86f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
