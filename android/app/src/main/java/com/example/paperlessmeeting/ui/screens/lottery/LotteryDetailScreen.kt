package com.example.paperlessmeeting.ui.screens.lottery

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.paperlessmeeting.domain.model.LotteryState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LotteryDetailScreen(
    meetingId: Int,
    meetingTitle: String,
    onBackClick: () -> Unit,
    viewModel: LotteryViewModel = hiltViewModel()
) {
    // Hardcoded current user for now (should come from SessionManager)
    // TODO: Replace with real user injection
    val currentUserId = 12 
    val currentUserName = "Android User"

    LaunchedEffect(Unit) {
        viewModel.init(meetingId, currentUserId, currentUserName)
    }

    val uiState by viewModel.uiState.collectAsState()
    val history by viewModel.history.collectAsState()
    val error by viewModel.error.collectAsState()

    // Error Handling
    LaunchedEffect(error) {
        if (error != null) {
            // Show toast or snackbar logic handled by scaffold usually
            // Here just log or you can add local state specifically for snackbar
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(meetingTitle) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Hero Status Card
            val status = uiState?.status ?: "IDLE"
            val isJoined = uiState?.participants?.any { it.id == currentUserId.toString() } == true
            
            StatusHeroCard(
                status = status,
                isJoined = isJoined,
                participantCount = uiState?.participant_count ?: 0,
                currentTitle = uiState?.current_title
            )

            // 2. Action Area
            ActionArea(
                status = status,
                isJoined = isJoined,
                onJoin = { viewModel.joinLottery() },
                onQuit = { viewModel.quitLottery() }
            )

            // 3. Winners List Header
            Text(
                text = "荣誉榜 (历史中奖)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )

            // 4. Winners List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (history.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("暂无中奖记录", color = Color.Gray)
                        }
                    }
                } else {
                    items(history) { round ->
                        if (round.status == "finished") {
                            WinnerRoundCard(round)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusHeroCard(
    status: String,
    isJoined: Boolean,
    participantCount: Int,
    currentTitle: String?
) {
    val backgroundColor by animateColorAsState(
        targetValue = when {
            status == "RESULT" -> Color(0xFFFFD700) // Gold
            status == "ROLLING" -> Color(0xFFFF9800) // Orange
            isJoined -> Color(0xFF2196F3) // Blue
            else -> Color(0xFFEEEEEE) // Gray
        },
        animationSpec = tween(500)
    )

    val contentColor = if (backgroundColor == Color(0xFFEEEEEE)) Color.Black else Color.White

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth().height(180.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (currentTitle != null && status != "IDLE") {
                    Text(
                        text = "正在进行：$currentTitle",
                        color = contentColor.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(8.dp))
                }
                
                Text(
                    text = when (status) {
                        "IDLE" -> "等待发起抽签"
                        "PREPARING" -> if (isJoined) "已入池，等待好运" else "抽签准备中"
                        "ROLLING" -> "🔒 抽签进行中..."
                        "RESULT" -> "🎉 结果已出炉"
                        else -> "未知状态"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                
                Spacer(Modifier.height(16.dp))
                
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = contentColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "$participantCount 人参与",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        color = contentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun ActionArea(
    status: String,
    isJoined: Boolean,
    onJoin: () -> Unit,
    onQuit: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (status == "ROLLING") {
            Button(
                onClick = {}, 
                enabled = false, 
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("正在抽签，无法操作")
            }
        } else {
            // Join/Quit Logic
            if (!isJoined) {
                Button(
                    onClick = onJoin,
                    modifier = Modifier.weight(1f).height(56.dp),
                    enabled = status != "IDLE" // Disable join if nothing happened? Or allow join anytime pending? Usually PREPARING allows join.
                ) {
                    Text("立即参与")
                }
            } else {
                OutlinedButton(
                    onClick = onQuit,
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("退出抽签")
                }
            }
        }
    }
}

@Composable
fun WinnerRoundCard(round: com.example.paperlessmeeting.domain.model.LotteryRound) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFC107))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = round.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                round.winners.forEach { winner ->
                    SuggestionChip(
                        onClick = {},
                        label = { Text(winner.user_name) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }
        }
    }
}

// Helper for FlowRow if not available in older compose handling
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FlowRow(
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    // Basic implementation reference or use Accompanist if needed
    // For now simplistic column fallback if flow row complex, 
    // but better use standard Layout or simple Row/Column if items few.
    // Assuming simple implementation or usage of latest compose 1.4+ FlowRow
    // If syntax error, revert to simple Column.
    // Compose 1.4+
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        content = { content() }
    )
}
