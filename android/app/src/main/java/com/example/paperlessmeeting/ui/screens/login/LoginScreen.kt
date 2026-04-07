package com.example.paperlessmeeting.ui.screens.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.paperlessmeeting.StartupTrace
import com.example.paperlessmeeting.ui.components.image.AppAsyncImage
import com.example.paperlessmeeting.ui.components.image.MeetingImageResolver

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val posterConfig by viewModel.posterConfig.collectAsState()
    var query by remember { mutableStateOf("") }
    var enableRemotePoster by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        StartupTrace.mark("LoginScreen.first_frame")
        enableRemotePoster = true
    }

    LaunchedEffect(enableRemotePoster) {
        if (!enableRemotePoster) return@LaunchedEffect
        StartupTrace.mark("LoginScreen.remote_poster.begin")
        viewModel.ensurePosterLoaded()
    }

    val posterModel = remember(enableRemotePoster, posterConfig.posterUrl, posterConfig.posterVersion) {
        MeetingImageResolver.loginPosterModel(
            posterUrl = posterConfig.posterUrl?.takeIf { enableRemotePoster },
            posterVersion = posterConfig.posterVersion?.takeIf { enableRemotePoster }
        )
    }

    LaunchedEffect(uiState) {
        when (uiState) {
            is LoginUiState.Success -> {
                val heartbeatRequest = OneTimeWorkRequestBuilder<com.example.paperlessmeeting.worker.HeartbeatWorker>()
                    .build()
                WorkManager.getInstance(context).enqueue(heartbeatRequest)
                // Consume success to avoid immediate auto-login when returning to LoginScreen after logout.
                viewModel.resetState()
                onLoginSuccess()
            }
            is LoginUiState.Error -> {
                // snackbarHostState.showSnackbar(state.message)
            }
            else -> {}
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0), // Disable default insets to handle manually
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Left Panel (Image)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                AppAsyncImage(
                    model = posterModel,
                    modifier = Modifier.fillMaxSize()
                )
                // Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 40.dp, bottom = 36.dp)
                ) {
                    Text(
                        text = "阅文系统",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "绿色 · 低碳 · 高效",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }

            // Right Panel (Form)
            Box(
                modifier = Modifier
                    .width(480.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.safeDrawing) // Ensure form clears safe area
                        .width(320.dp)
                        .padding(24.dp)
                ) {
                    Text(
                        text = "欢迎使用阅文系统",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("姓名 或 手机号") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { viewModel.login(query) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        enabled = uiState !is LoginUiState.Loading,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (uiState is LoginUiState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp), 
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("进入会议", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        }
                    }
                    
                    if (uiState is LoginUiState.Error) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = (uiState as LoginUiState.Error).message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
