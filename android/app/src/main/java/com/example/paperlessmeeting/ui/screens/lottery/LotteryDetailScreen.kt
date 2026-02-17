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
    LaunchedEffect(Unit) {
        viewModel.init(meetingId)
    }

    val uiState by viewModel.uiState.collectAsState()
    val history by viewModel.history.collectAsState()
    val error by viewModel.error.collectAsState()

    // Error Handling
    LaunchedEffect(error) {
        if (error != null) {
            viewModel.clearError()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    // Show error in snackbar
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
        }
    }

    // \u2b50 \u4e2d\u5956\u63d0\u793a\u5bf9\u8bdd\u6846
    var winnerDialogData by remember { mutableStateOf<WinnerAnnouncementData?>(null) }
    
    LaunchedEffect(Unit) {
        viewModel.winnerAnnouncement.collect { data ->
            winnerDialogData = data
        }
    }

    // \u663e\u793a\u4e2d\u5956\u5bf9\u8bdd\u6846
    winnerDialogData?.let { data ->
        AlertDialog(
            onDismissRequest = { winnerDialogData = null },
            confirmButton = {
                Button(onClick = { winnerDialogData = null }) {
                    Text("\u592a\u68d2\u4e86\uff01")
                }
            },
            title = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("\ud83c\udf89")
                    Text("\u606d\u559c\u4e2d\u5956!")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "\u3010${data.roundTitle}\u3011",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text("${data.userName},\u60a8\u5df2\u6210\u529f\u4e2d\u9009\uff01")
                    Text(
                        text = "\u8bf7\u5728\u5927\u5c4f\u67e5\u770b\u8be6\u7ec6\u7ed3\u679c~",
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
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        // ⭐ 使用局部变量避免 smart cast 问题
        val currentState = uiState
        
        // ⭐ 加载状态:等待服务器返回初始状态
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

        // ⭐ 正常UI:已收到状态
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Hero Status Card
            val status = currentState.status
            val isJoined = currentState.is_joined
            val hasHistory = history.isNotEmpty()  // ⭐ 检查是否有历史记录
            
            StatusHeroCard(
                status = status,
                isJoined = isJoined,
                participantCount = currentState.participant_count,
                currentTitle = currentState.current_title,
                hasHistory = hasHistory  // ⭐ 传递历史记录标志
            )

            // 2. Action Area
            ActionArea(
                status = status,
                isJoined = isJoined,
                hasHistory = hasHistory,  // ⭐ 传递历史记录标志
                onJoin = { viewModel.joinLottery() },
                onQuit = { viewModel.quitLottery() }
            )

            // 3. Winners List Header
            Text(
                text = "抽签结果",
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
    currentTitle: String?,
    hasHistory: Boolean = false  // ⭐ 新增参数
) {
    // ⭐ 渐变背景配色方案
    val backgroundBrush = when {
        status == "RESULT" -> Brush.linearGradient(
            colors = listOf(Color(0xFFFFD700), Color(0xFFFFAA00)) // 金色→琥珀色
        )
        status == "ROLLING" -> Brush.linearGradient(
            colors = listOf(Color(0xFFFF6B35), Color(0xFFFF9500)) // 红橙→橙色
        )
        isJoined && status == "PREPARING" -> Brush.linearGradient(
            colors = listOf(Color(0xFF00BCD4), Color(0xFF2196F3)) // 青色→蓝色
        )
        // ⭐ IDLE状态:如果有历史记录,使用金色;否则使用灰色
        status == "IDLE" && hasHistory -> Brush.linearGradient(
            colors = listOf(Color(0xFFFFD700), Color(0xFFFFAA00)) // 金色→琥珀色(完成状态)
        )
        else -> Brush.linearGradient(
            colors = listOf(Color(0xFF90A4AE), Color(0xFFB0BEC5)) // 蓝灰渐变
        )
    }

    val contentColor = if (status == "IDLE" && !hasHistory && !isJoined) Color.Black else Color.White

    Card(
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier.fillMaxWidth().height(200.dp)
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
                if (currentTitle != null && status != "IDLE") {
                    Text(
                        text = "正在进行:$currentTitle",
                        color = contentColor.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // 状态文本 + 图标
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val (statusText, statusIcon) = when {
                        // ⭐ IDLE状态优化:区分"等待配置"和"全部完成"
                        status == "IDLE" && hasHistory -> "所有轮次已完成" to "✨"
                        status == "IDLE" -> "等待发起抽签" to "⏳"
                        status == "PREPARING" && isJoined -> "已入池,等待好运" to "✅"
                        status == "PREPARING" -> "抽签准备中" to "⏰"
                        status == "ROLLING" -> "抽签进行中..." to "🎲"
                        status == "RESULT" -> "结果已出炉" to "🎉"
                        else -> "未知状态" to "❓"
                    }
                    
                    Text(
                        text = statusIcon,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                }
                
                Spacer(Modifier.height(8.dp))
                
                // 参与者数量徽章
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
                        Text(
                            text = "👥",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "$participantCount 人参与",
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
fun ActionArea(
    status: String,
    isJoined: Boolean,
    hasHistory: Boolean = false,  // ⭐ 新增参数:是否有历史记录
    onJoin: () -> Unit,
    onQuit: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ⭐ 所有轮次完成(IDLE + 有历史记录)时显示完成状态
        if (status == "IDLE" && hasHistory) {
            Button(
                onClick = {}, 
                enabled = false, 
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("✨ 所有轮次已完成")
            }
        }
        // ⭐ ROLLING和RESULT状态不允许任何操作
        else if (status == "ROLLING" || status == "RESULT") {
            Button(
                onClick = {}, 
                enabled = false, 
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(
                    when (status) {
                        "ROLLING" -> "🔒 抽签进行中,无法操作"
                        "RESULT" -> "🎉 抽签已结束"
                        else -> "无法操作"
                    }
                )
            }
        } else {
            // Join/Quit Logic (only in IDLE without history or PREPARING)
            if (!isJoined) {
                Button(
                    onClick = onJoin,
                    modifier = Modifier.weight(1f).height(56.dp),
                    enabled = status == "PREPARING" // Only allow join when PREPARING
                ) {
                    Text(if (status == "IDLE") "等待管理员配置" else "立即参与")
                }
            } else {
                OutlinedButton(
                    onClick = onQuit,
                    modifier = Modifier.weight(1f).height(56.dp),
                    enabled = status == "PREPARING", // ⭐ 只在PREPARING时允许退出
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("退出抽签")
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun WinnerRoundCard(round: com.example.paperlessmeeting.domain.model.LotteryRound) {
    // ⭐ 使用统一的优雅渐变色 - 柔和的蓝紫色调
    val gradient = Brush.linearGradient(
        colors = listOf(Color(0xFF6B9FFF), Color(0xFF4A90E2)) // 淡蓝渐变,协调不突兀
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
                // 标题行
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
                
                // ⭐ 中奖者标签 - 移除外层Surface,直接显示白色徽章
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
                                Text(
                                    text = "🎉",
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = winner.user_name,
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
