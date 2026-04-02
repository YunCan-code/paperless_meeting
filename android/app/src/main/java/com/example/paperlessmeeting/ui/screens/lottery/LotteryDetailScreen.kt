package com.example.paperlessmeeting.ui.screens.lottery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.paperlessmeeting.domain.model.LotteryRound
import com.example.paperlessmeeting.domain.model.LotterySession
import com.example.paperlessmeeting.domain.model.LotteryWinner
import com.example.paperlessmeeting.ui.components.notice.LocalAppNoticeController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LotteryDetailScreen(
    meetingId: Int,
    meetingTitle: String,
    onBackClick: () -> Unit,
    viewModel: LotteryViewModel = hiltViewModel()
) {
    val noticeController = LocalAppNoticeController.current

    LaunchedEffect(meetingId) {
        viewModel.init(meetingId)
    }

    val uiState by viewModel.uiState.collectAsState()
    val history by viewModel.history.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(error) {
        error?.let {
            noticeController.showMessage(it)
            viewModel.clearError()
        }
    }

    var winnerDialogData by remember { mutableStateOf<WinnerAnnouncementData?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.winnerAnnouncement.collect { data ->
            winnerDialogData = data
        }
    }

    winnerDialogData?.let { data ->
        AlertDialog(
            onDismissRequest = { winnerDialogData = null },
            confirmButton = {
                Button(onClick = { winnerDialogData = null }) {
                    Text("太棒了！")
                }
            },
            title = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🎉")
                    Text("恭喜中奖！")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "【${data.roundTitle}】",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text("${data.userName}，您已成功中选！")
                    Text(
                        text = "请在大屏查看完整开奖结果。",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(meetingTitle) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        val currentState = uiState
        if (currentState == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text("加载抽签状态中...", color = Color.Gray)
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatusHeroCard(currentState)

            ActionArea(
                session = currentState,
                onJoin = viewModel::joinLottery,
                onQuit = viewModel::quitLottery
            )

            Text(
                text = "抽签结果",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (history.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("暂无中奖记录", color = Color.Gray)
                        }
                    }
                } else {
                    items(history) { round ->
                        WinnerRoundCard(round)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusHeroCard(session: LotterySession) {
    val status = session.session_status
    val hasHistory = session.rounds.any { it.status == "finished" }
    val currentTitle = session.current_round?.title

    val backgroundBrush = when {
        status == "result" || status == "completed" -> Brush.linearGradient(
            colors = listOf(Color(0xFFFFD700), Color(0xFFFFAA00))
        )
        status == "rolling" -> Brush.linearGradient(
            colors = listOf(Color(0xFFFF6B35), Color(0xFFFF9500))
        )
        session.joined && status in setOf("collecting", "ready") -> Brush.linearGradient(
            colors = listOf(Color(0xFF00BCD4), Color(0xFF2196F3))
        )
        status == "idle" && hasHistory -> Brush.linearGradient(
            colors = listOf(Color(0xFFFFD700), Color(0xFFFFAA00))
        )
        else -> Brush.linearGradient(
            colors = listOf(Color(0xFF90A4AE), Color(0xFFB0BEC5))
        )
    }

    val contentColor = if (status == "idle" && !hasHistory && !session.joined) Color.Black else Color.White
    val (statusText, statusIcon) = when {
        status == "completed" || (status == "idle" && session.all_rounds_finished) -> "所有轮次已完成" to "✨"
        status == "idle" -> "等待发起抽签" to "⏳"
        status in setOf("collecting", "ready") && session.joined -> "已入池，等待好运" to "✅"
        status == "ready" -> "轮次已准备" to "🎯"
        status == "collecting" -> "抽签准备中" to "⏰"
        status == "rolling" -> "抽签进行中..." to "🎲"
        status == "result" -> "结果已出炉" to "🎉"
        else -> "抽签进行中" to "🎲"
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = backgroundBrush)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!currentTitle.isNullOrBlank() && status != "idle") {
                    Text(
                        text = "当前轮次：$currentTitle",
                        color = contentColor.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = statusIcon, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                }

                Spacer(Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = contentColor.copy(alpha = 0.25f),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(text = "👥", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "${session.participants_count} 人参与",
                            color = contentColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionArea(
    session: LotterySession,
    onJoin: () -> Unit,
    onQuit: () -> Unit
) {
    val status = session.session_status
    val joinEnabled = status in setOf("collecting", "ready")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when {
            session.all_rounds_finished || status == "completed" -> {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("✨ 所有轮次已完成")
                }
            }

            status == "rolling" -> {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("🔒 抽签进行中，无法操作")
                }
            }

            status == "result" -> {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("🎉 本轮已开奖")
                }
            }

            session.current_round == null -> {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("等待管理员准备轮次")
                }
            }

            !session.joined -> {
                Button(
                    onClick = onJoin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = joinEnabled
                ) {
                    Text(if (joinEnabled) "立即参与" else "暂不可参与")
                }
            }

            else -> {
                OutlinedButton(
                    onClick = onQuit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = joinEnabled,
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("退出抽签")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WinnerRoundCard(round: LotteryRound) {
    val gradient = Brush.linearGradient(
        colors = listOf(Color(0xFF6B9FFF), Color(0xFF4A90E2))
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush = gradient)
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = round.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                FlowRow(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    round.winners.forEach { winner ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White,
                            shadowElevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "🎉", fontSize = 14.sp)
                                Text(
                                    text = winner.displayName(),
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF333333)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun LotteryWinner.displayName(): String = name ?: user_name ?: "未知用户"
