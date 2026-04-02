package com.example.paperlessmeeting.ui.screens.lottery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.paperlessmeeting.domain.model.LotteryHistoryResponse
import com.example.paperlessmeeting.domain.model.LotteryRound
import com.example.paperlessmeeting.domain.model.LotterySession
import com.example.paperlessmeeting.domain.model.Meeting
import com.example.paperlessmeeting.ui.components.formatTimeRange
import com.example.paperlessmeeting.ui.components.lottery.LotteryChipTone
import com.example.paperlessmeeting.ui.components.lottery.LotteryEmptyStateCard
import com.example.paperlessmeeting.ui.components.lottery.LotteryMetaChip
import com.example.paperlessmeeting.ui.components.lottery.LotteryRoundRecordCard
import com.example.paperlessmeeting.ui.components.lottery.LotterySectionHeader
import com.example.paperlessmeeting.ui.components.lottery.LotteryStatusPill
import com.example.paperlessmeeting.ui.theme.CardBackground
import com.example.paperlessmeeting.ui.theme.PrimaryBlue
import com.example.paperlessmeeting.ui.theme.TextPrimary
import com.example.paperlessmeeting.ui.theme.TextSecondary

private val LotteryPageBackground = Color(0xFFF3F6FB)
private val LotteryCardBorder = Color(0xFFDCE4F0)
private val LotteryRecordBorder = Color(0xFFE4EAF3)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LotteryListScreen(
    navController: NavController,
    viewModel: LotteryListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var winnerAnnouncement by remember { mutableStateOf<WinnerAnnouncementData?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.winnerAnnouncement.collect { announcement ->
            winnerAnnouncement = announcement
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "抽签中心",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新", tint = PrimaryBlue)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = LotteryPageBackground)
            )
        },
        containerColor = LotteryPageBackground
    ) { innerPadding ->
        if (uiState.isLoading && uiState.currentDisplayMeeting == null && uiState.historyLotteries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PrimaryBlue)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (!uiState.error.isNullOrBlank()) {
                item {
                    LotteryInlineErrorCard(message = uiState.error ?: "加载失败")
                }
            }

            item {
                LotterySectionHeader(title = "当前抽签")
            }

            item {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val isWideLayout = maxWidth >= 720.dp
                    if (isWideLayout) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            CurrentLotteryPanel(
                                meeting = uiState.currentDisplayMeeting,
                                session = uiState.currentDisplaySession,
                                actionError = uiState.currentActionError,
                                actionInProgress = uiState.actionInProgress,
                                onJoin = viewModel::joinCurrentDisplayLottery,
                                onQuit = viewModel::quitCurrentDisplayLottery,
                                modifier = Modifier.weight(7f)
                            )
                            CurrentLotteryResultPanel(
                                meeting = uiState.currentDisplayMeeting,
                                resultRound = uiState.currentDisplayResultRound,
                                session = uiState.currentDisplaySession,
                                modifier = Modifier.weight(3f)
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CurrentLotteryPanel(
                                meeting = uiState.currentDisplayMeeting,
                                session = uiState.currentDisplaySession,
                                actionError = uiState.currentActionError,
                                actionInProgress = uiState.actionInProgress,
                                onJoin = viewModel::joinCurrentDisplayLottery,
                                onQuit = viewModel::quitCurrentDisplayLottery
                            )
                            CurrentLotteryResultPanel(
                                meeting = uiState.currentDisplayMeeting,
                                resultRound = uiState.currentDisplayResultRound,
                                session = uiState.currentDisplaySession
                            )
                        }
                    }
                }
            }

            item {
                LotterySectionHeader(title = "历史抽签记录")
            }

            if (uiState.historyLotteries.isEmpty()) {
                item {
                    LotteryEmptyStateCard(
                        icon = Icons.Default.History,
                        title = "暂无抽签历史",
                        description = "产生过抽签结果的会议会显示在这里。"
                    )
                }
            } else {
                items(uiState.historyLotteries, key = { it.meeting_id }) { history ->
                    LotteryHistoryGroupCard(history = history)
                }
            }
        }

        winnerAnnouncement?.let { announcement ->
            AlertDialog(
                onDismissRequest = { winnerAnnouncement = null },
                title = {
                    Text(
                        text = announcement.title,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = announcement.message,
                        color = TextSecondary,
                        lineHeight = 21.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = { winnerAnnouncement = null }) {
                        Text("知道了", color = PrimaryBlue, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CurrentLotteryPanel(
    meeting: Meeting?,
    session: LotterySession?,
    actionError: String?,
    actionInProgress: Boolean,
    onJoin: () -> Unit,
    onQuit: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (meeting == null) {
        LotteryEmptyStateCard(
            icon = Icons.Default.Schedule,
            title = "当前暂无可展示会议",
            description = "今天还没有可展示的抽签会话。",
            modifier = modifier
        )
        return
    }

    val displayRound = session?.displayRound()
    val headerState = session?.headerState()
    val actionState = session?.actionState()

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = CardBackground,
        border = BorderStroke(1.dp, LotteryCardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFFF7FAFF),
                            Color(0xFFEAF2FF)
                        )
                    )
                )
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        LotteryStatusPill(
                            text = headerState?.lifecycleLabel ?: "未开始",
                            tone = headerState?.lifecycleTone ?: LotteryChipTone.Neutral
                        )
                        displayRound?.let {
                            LotteryMetaChip(text = it.roundOrderLabel(), tone = LotteryChipTone.Primary)
                            LotteryMetaChip(text = "抽取 ${it.count} 人", tone = LotteryChipTone.Neutral)
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    LotteryMetaChip(
                        text = headerState?.attendeeLabel ?: "待开始",
                        tone = headerState?.attendeeTone ?: LotteryChipTone.Neutral,
                        leadingIcon = if (session?.joined == true) Icons.Default.CheckCircle else Icons.Default.Schedule
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = meeting.title,
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = displayRound?.title ?: "等待主持人开始抽签",
                        color = TextPrimary,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = headerState?.supportingText ?: "当前暂无抽签会话，请等待主持人设置轮次。",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 21.sp
                    )
                    Text(
                        text = formatTimeRange(meeting.startTime, meeting.endTime),
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LotteryMetaChip(
                        text = "${session?.participants_count ?: 0} 人在抽签池",
                        tone = LotteryChipTone.Neutral,
                        leadingIcon = Icons.Default.Groups
                    )
                    LotteryMetaChip(
                        text = session?.allowRepeatLabel() ?: "重复规则待定",
                        tone = if (displayRound?.allow_repeat == true) LotteryChipTone.Warning else LotteryChipTone.Neutral
                    )
                    LotteryMetaChip(
                        text = session?.next_round?.let { "下一轮：${it.roundOrderLabel()}" } ?: "下一轮：待定",
                        tone = if (session?.next_round != null) LotteryChipTone.Success else LotteryChipTone.Neutral
                    )
                }

                if (!actionError.isNullOrBlank()) {
                    LotteryInlineErrorCard(message = actionError)
                }

                actionState?.let { state ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = actionStateBackground(state.tone)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = state.message,
                                color = TextPrimary,
                                style = MaterialTheme.typography.bodyMedium
                            )

                            if (state.primaryLabel != null || state.secondaryLabel != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    state.primaryLabel?.let { label ->
                                        Button(
                                            onClick = onJoin,
                                            enabled = state.primaryEnabled && !actionInProgress,
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                                        ) {
                                            if (actionInProgress) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    strokeWidth = 2.dp,
                                                    color = Color.White
                                                )
                                            } else {
                                                Text(label, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    state.secondaryLabel?.let { label ->
                                        OutlinedButton(
                                            onClick = onQuit,
                                            enabled = state.secondaryEnabled && !actionInProgress,
                                            modifier = Modifier.weight(1f),
                                            border = BorderStroke(1.dp, Color(0xFFF4A3A3)),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB42318))
                                        ) {
                                            if (actionInProgress) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    strokeWidth = 2.dp,
                                                    color = Color(0xFFB42318)
                                                )
                                            } else {
                                                Text(label, fontWeight = FontWeight.Bold)
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
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CurrentLotteryResultPanel(
    meeting: Meeting?,
    resultRound: LotteryRound?,
    session: LotterySession?,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = CardBackground,
        border = BorderStroke(1.dp, LotteryCardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "当前中签结果",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            when {
                meeting == null -> {
                    LotteryEmptyStateCard(
                        icon = Icons.Default.EmojiEvents,
                        title = "暂无结果",
                        description = "先选择一个可展示的抽签会议。",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                resultRound == null || resultRound.winners.isEmpty() -> {
                    LotteryEmptyStateCard(
                        icon = Icons.Default.EmojiEvents,
                        title = "结果暂未产生",
                        description = "当前会议还没有可展示的中签名单。",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                else -> {
                    val isCurrentResult = session?.currentResultRound()?.id == resultRound.id
                    LotteryStatusPill(
                        text = if (isCurrentResult) "当前结果" else "最近结果",
                        tone = if (isCurrentResult) LotteryChipTone.Warning else LotteryChipTone.Neutral
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = resultRound.roundOrderLabel(),
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = resultRound.title,
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "抽取 ${resultRound.winners.size} 人",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        resultRound.winners.forEach { winner ->
                            LotteryMetaChip(
                                text = winner.displayName(),
                                tone = LotteryChipTone.Primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LotteryHistoryGroupCard(history: LotteryHistoryResponse) {
    val displayRounds = history.rounds
        .filter { it.status == "finished" || it.winners.isNotEmpty() }
        .sortedWith(
            compareByDescending<LotteryRound> { if (it.sort_order > 0) it.sort_order else Int.MIN_VALUE }
                .thenByDescending { it.id }
        )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFFCFDFF),
        border = BorderStroke(1.dp, LotteryRecordBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = history.meeting_title,
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            displayRounds.forEachIndexed { index, round ->
                LotteryRoundRecordCard(
                    title = "${round.roundOrderLabel()} · ${round.title}",
                    statusLabel = round.roundStatusLabel(),
                    statusTone = round.roundStatusTone(),
                    metaText = round.metaSummary(),
                    winnerSummary = round.winnerNamesSummary(),
                    highlighted = index == 0
                )
                if (index != displayRounds.lastIndex) {
                    HorizontalDivider(color = Color(0xFFE7EDF5))
                }
            }
        }
    }
}

@Composable
private fun LotteryInlineErrorCard(message: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFFFF7ED),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            color = Color(0xFF9A3412),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        )
    }
}

private fun actionStateBackground(tone: LotteryChipTone): Color {
    return when (tone) {
        LotteryChipTone.Primary -> Color(0xFFEAF2FF)
        LotteryChipTone.Success -> Color(0xFFEAF8EF)
        LotteryChipTone.Warning -> Color(0xFFFFF4E5)
        LotteryChipTone.Danger -> Color(0xFFFFECEC)
        LotteryChipTone.Neutral -> Color(0xFFF5F7FA)
    }
}
