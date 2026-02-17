package com.example.paperlessmeeting.ui.screens.lottery

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.paperlessmeeting.domain.model.LotteryHistoryResponse
import com.example.paperlessmeeting.domain.model.Meeting
import com.example.paperlessmeeting.ui.navigation.Screen

// Minimalist Color Palette
private val ActiveGold = Color(0xFFFFD700) // Gold for lottery
private val SurfaceWhite = Color(0xFFFFFFFF)
private val TextPrimary = Color(0xFF111827)
private val TextSecondary = Color(0xFF6B7280)
private val LightBackground = Color(0xFFF9FAFB)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LotteryListScreen(
    navController: NavController,
    viewModel: LotteryListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.loadData() // Refresh logic could check timestamp to avoid redundant loads
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "抽签中心",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = SurfaceWhite
                )
            )
        },
        containerColor = LightBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Minimalist Tabs
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = SurfaceWhite,
                contentColor = ActiveGold,
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = ActiveGold,
                        height = 3.dp
                    )
                },
                divider = { Divider(color = Color.Transparent) }
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = {
                        Text(
                            "今日会议 (${uiState.activeLotteries.size})",
                            fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTabIndex == 0) TextPrimary else TextSecondary
                        )
                    }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = {
                        Text(
                            "历史记录",
                            fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTabIndex == 1) TextPrimary else TextSecondary
                        )
                    }
                )
            }

            Spacer(Modifier.height(16.dp))

            // Content
            AnimatedContent(
                targetState = selectedTabIndex,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith
                            fadeOut(animationSpec = tween(300))
                },
                label = "LotteryTabTransition"
            ) { targetIndex ->
                 if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ActiveGold)
                    }
                 } else if (uiState.error != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                         Text(
                             text = "Error: ${uiState.error}",
                             color = MaterialTheme.colorScheme.error,
                             modifier = Modifier.padding(16.dp)
                         )
                    }
                 } else {
                     if (targetIndex == 0) {
                         // Active List
                         if (uiState.activeLotteries.isEmpty()) {
                             EmptyStateView("今日暂无会议")
                         } else {
                             LazyColumn(
                                 verticalArrangement = Arrangement.spacedBy(16.dp),
                                 contentPadding = PaddingValues(16.dp)
                             ) {
                                 items(uiState.activeLotteries) { meeting ->
                                     ActiveMeetingCard(meeting) {
                                         // Navigate to lottery detail
                                         navController.navigate(Screen.LotteryDetail.createRoute(meeting.id, meeting.title))
                                     }
                                 }
                             }
                         }
                     } else {
                         // History List
                         if (uiState.historyLotteries.isEmpty()) {
                             EmptyStateView("暂无抽签历史")
                         } else {
                             LazyColumn(
                                 verticalArrangement = Arrangement.spacedBy(16.dp),
                                 contentPadding = PaddingValues(16.dp)
                             ) {
                                 items(uiState.historyLotteries) { history ->
                                     HistoryGroupCard(history, navController)
                                 }
                             }
                         }
                     }
                 }
            }
        }
    }
}

@Composable
fun ActiveMeetingCard(meeting: Meeting, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 2.dp,
        color = SurfaceWhite,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(ActiveGold.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = ActiveGold)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = meeting.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = meeting.startTime.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun HistoryGroupCard(history: LotteryHistoryResponse, navController: NavController) {
    if (history.rounds.isEmpty()) return

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = history.meeting_title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            history.rounds.forEach { round ->
                HistoryRoundItem(round) {
                     navController.navigate(Screen.LotteryDetail.createRoute(history.meeting_id, history.meeting_title))
                }
                if (round != history.rounds.last()) {
                    Divider(color = LightBackground, thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
fun HistoryRoundItem(round: com.example.paperlessmeeting.domain.model.LotteryRound, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
         Column(modifier = Modifier.weight(1f)) {
             Text(
                 text = "${round.title} (${round.count}人)",
                 style = MaterialTheme.typography.bodyMedium,
                 fontWeight = FontWeight.Medium
             )
             if (round.winners.isNotEmpty()) {
                 Text(
                     text = "中奖: ${round.winners.joinToString { it.user_name }}",
                     style = MaterialTheme.typography.bodySmall,
                     color = TextSecondary,
                     maxLines = 1
                 )
             }
         }
         
         Surface(
             color = if (round.status == "finished") Color.Green.copy(alpha=0.1f) else Color.Gray.copy(alpha=0.1f),
             shape = RoundedCornerShape(4.dp)
         ) {
             Text(
                 text = if(round.status == "finished") "已结束" else "未完成",
                 modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                 style = MaterialTheme.typography.labelSmall,
                 color = if (round.status == "finished") Color.Green else Color.Gray
             )
         }
    }
}

@Composable
fun EmptyStateView(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = Color.LightGray.copy(alpha = 0.5f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(24.dp))
            Text(
                message,
                color = TextSecondary.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
