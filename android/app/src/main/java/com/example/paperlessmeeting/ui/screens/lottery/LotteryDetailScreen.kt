package com.example.paperlessmeeting.ui.screens.lottery

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LotteryDetailScreen(
    meetingId: Int,
    meetingTitle: String,
    onNavigateBack: () -> Unit,
    viewModel: LotteryViewModel = hiltViewModel()
) {
    // 监听 meetingId 变化，连接 Socket
    LaunchedEffect(meetingId) {
        viewModel.connectToMeeting(meetingId)
    }

    val socketConnected = viewModel.socketConnected
    val roundTitle = viewModel.currentRoundTitle
    val myStatus = viewModel.myStatus
    val participantsCount = viewModel.participantsCount
    val winnerIds = viewModel.winnerIds.collectAsState(initial = emptySet()).value
    val currentUserId = viewModel.getCurrentUserId()
    val isWinner = winnerIds.contains(currentUserId)
    val isRolling = viewModel.lotteryStatus == "ROLLING"
    
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.events.collect { event: LotteryEvent ->
            android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(meetingTitle) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 连接状态
            if (!socketConnected) {
                Text(
                    text = "正在连接抽签服务器...",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(32.dp))
                LinearProgressIndicator(modifier = Modifier.width(200.dp))
            } else {
                // 标题
                Text(
                    text = roundTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "当前参与人数: $participantsCount",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(48.dp))

                // 滚动中状态提示
                if (isRolling) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A5F)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF00D9FF),
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "🎰 抽签进行中...",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00D9FF)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "请关注大屏幕，等待结果揭晓",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                // 操作区域
                else if (viewModel.isMeetingFinished) {
                     Card(
                         colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                         shape = RoundedCornerShape(16.dp),
                         modifier = Modifier.fillMaxWidth()
                     ) {
                         Column(
                             modifier = Modifier
                                 .padding(24.dp)
                                 .fillMaxWidth(),
                             horizontalAlignment = Alignment.CenterHorizontally
                         ) {
                             Text(
                                 text = "本场抽签已全部结束",
                                 style = MaterialTheme.typography.titleMedium,
                                 fontWeight = FontWeight.Bold,
                                 color = MaterialTheme.colorScheme.onSurfaceVariant
                             )
                             Spacer(modifier = Modifier.height(8.dp))
                             Text(
                                 text = "感谢您的关注",
                                 style = MaterialTheme.typography.bodyMedium,
                                 color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                             )
                         }
                     }
                } else if (isWinner) {
                     // Winner Celebration Card
                     Card(
                         colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)), // Light Yellow
                         border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFBBF24)),
                         shape = RoundedCornerShape(16.dp),
                         modifier = Modifier.fillMaxWidth()
                     ) {
                         Column(
                             modifier = Modifier
                                 .padding(24.dp)
                                 .fillMaxWidth(),
                             horizontalAlignment = Alignment.CenterHorizontally
                         ) {
                             Text(
                                 text = "🎉 恭喜中签！ 🎉",
                                 style = MaterialTheme.typography.headlineMedium,
                                 fontWeight = FontWeight.Black,
                                 color = Color(0xFFB45309)
                             )
                             Spacer(modifier = Modifier.height(12.dp))
                             Text(
                                 text = "您已成为幸运儿",
                                 style = MaterialTheme.typography.bodyLarge,
                                 color = Color(0xFF92400E)
                             )
                         }
                     }
                } else {
                    when (myStatus) {
                        is ParticipationStatus.NotJoined -> {
                            Button(
                                onClick = { viewModel.joinLottery() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("立即参与抽签", fontSize = 18.sp)
                            }
                        }
                        is ParticipationStatus.Joined -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(24.dp)
                                        .fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "🎉 已成功参与",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "请关注大屏幕查看结果",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                            
                            // --- 退出按钮 (滚动中禁用) ---
                            Spacer(modifier = Modifier.height(16.dp))
                            TextButton(
                                onClick = { viewModel.quitLottery() },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                enabled = !isRolling
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isRolling) "抽签中无法退出" else "退出本次抽签")
                            }
                        }
                        is ParticipationStatus.Removed -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(24.dp)
                                        .fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning, 
                                        contentDescription = "Removed",
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "您已被移出本轮抽签",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Button(
                                        onClick = { viewModel.joinLottery() },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("重新加入", color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
