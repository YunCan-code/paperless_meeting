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
    // Collect winners
    val winnersList = viewModel.winners.collectAsState(initial = emptyList()).value

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

                // 操作区域
                // 操作区域
                if (viewModel.isMeetingFinished) {
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
                
                // Show Winners List
                Spacer(modifier = Modifier.height(32.dp))
                if (winnersList.isNotEmpty()) {
                    Text(
                        text = "🏆 中奖红榜",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            winnersList.forEach { name ->
                                Text(
                                    text = "🎉 $name",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.5f))
                            }
                        }
                    }
                }
            }
        }
    }
}
