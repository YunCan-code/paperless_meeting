package com.example.paperlessmeeting.ui.screens.lottery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.example.paperlessmeeting.domain.model.LotteryHistoryResponse
import com.example.paperlessmeeting.domain.model.LotteryRound
import com.example.paperlessmeeting.domain.model.LotterySession
import com.example.paperlessmeeting.domain.model.Meeting
import com.example.paperlessmeeting.ui.components.lottery.LotteryActionBar
import com.example.paperlessmeeting.ui.components.lottery.LotteryCardBorderColor
import com.example.paperlessmeeting.ui.components.lottery.LotteryCardFrame
import com.example.paperlessmeeting.ui.components.lottery.LotteryChipTone
import com.example.paperlessmeeting.ui.components.lottery.LotteryEmptyStateCard
import com.example.paperlessmeeting.ui.components.lottery.LotteryInlineEmptyState
import com.example.paperlessmeeting.ui.components.lottery.LotteryMetaChip
import com.example.paperlessmeeting.ui.components.lottery.LotteryRoundRecordCard
import com.example.paperlessmeeting.ui.components.lottery.LotterySectionHeader
import com.example.paperlessmeeting.ui.components.lottery.LotterySlotMachineStage
import com.example.paperlessmeeting.ui.components.lottery.LotteryStagePlaceholder
import com.example.paperlessmeeting.ui.components.lottery.LotteryStatusPill
import com.example.paperlessmeeting.ui.components.lottery.LotterySupportInfoRow
import com.example.paperlessmeeting.ui.components.lottery.LotteryWinnerStage
import com.example.paperlessmeeting.ui.theme.PrimaryBlue
import com.example.paperlessmeeting.ui.theme.TextPrimary
import com.example.paperlessmeeting.ui.theme.TextSecondary

private val LotteryPageBackground = Color(0xFFF3F6FB)
private val LotteryRecordBorder = LotteryCardBorderColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LotteryListScreen(
    navController: NavController,
    isActive: Boolean = true,
    viewModel: LotteryListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var winnerAnnouncement by remember { mutableStateOf<WinnerAnnouncementData?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.winnerAnnouncement.collect { announcement ->
            winnerAnnouncement = announcement
        }
    }

    LaunchedEffect(isActive) {
        if (isActive) {
            viewModel.refreshOnVisible()
        }
    }

    DisposableEffect(lifecycleOwner, isActive) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && isActive) {
                viewModel.refreshOnVisible()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val isWideLayout = maxWidth >= 720.dp
                    if (isWideLayout) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(7f)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                LotterySectionHeader(title = "当前抽签")
                                CurrentLotteryPanel(
                                    meeting = uiState.currentDisplayMeeting,
                                    session = uiState.currentDisplaySession,
                                    actionError = uiState.currentActionError,
                                    actionInProgress = uiState.actionInProgress,
                                    onJoin = viewModel::joinCurrentDisplayLottery,
                                    onQuit = viewModel::quitCurrentDisplayLottery,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .weight(3f)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                LotterySectionHeader(title = "中签结果")
                                CurrentLotteryResultPanel(
                                    meeting = uiState.currentDisplayMeeting,
                                    resultRound = uiState.currentDisplayResultRound,
                                    session = uiState.currentDisplaySession,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            LotterySectionHeader(title = "当前抽签")
                            CurrentLotteryPanel(
                                meeting = uiState.currentDisplayMeeting,
                                session = uiState.currentDisplaySession,
                                actionError = uiState.currentActionError,
                                actionInProgress = uiState.actionInProgress,
                                onJoin = viewModel::joinCurrentDisplayLottery,
                                onQuit = viewModel::quitCurrentDisplayLottery
                            )
                            LotterySectionHeader(title = "中签结果")
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
                    LotteryHistoryGroupCard(
                        history = history,
                        expanded = uiState.expandedHistoryMeetingIds.contains(history.meeting_id),
                        onToggle = { viewModel.toggleHistoryMeeting(history.meeting_id) }
                    )
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
    val nextRoundHint = session?.nextRoundHint(displayRound)
    val stageMode = session?.stageMode() ?: LotteryStageMode.Idle
    val stageNames = session?.slotMachineNames().orEmpty()
    val currentResultRound = session?.currentResultRound()

    LotteryCardFrame(modifier = modifier.fillMaxHeight()) {
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
                LotteryMetaChip(
                    text = "${session?.participants_count ?: 0} 人在抽签池",
                    tone = LotteryChipTone.Neutral,
                    leadingIcon = Icons.Default.Groups
                )
                LotteryMetaChip(
                    text = session?.allowRepeatLabel() ?: "重复规则待定",
                    tone = if (displayRound?.allow_repeat == true) LotteryChipTone.Warning else LotteryChipTone.Neutral
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            LotteryMetaChip(
                text = headerState?.attendeeLabel ?: "待开始",
                tone = headerState?.attendeeTone ?: LotteryChipTone.Neutral,
                leadingIcon = if (session?.joined == true) Icons.Default.CheckCircle else Icons.Default.Schedule
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = meeting.title,
                color = TextSecondary,
                style = MaterialTheme.typography.labelMedium,
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
        }

        nextRoundHint?.let {
            LotterySupportInfoRow(
                label = "下一轮",
                value = it.removePrefix("下一轮：")
            )
        }

        when (stageMode) {
            LotteryStageMode.Rolling -> {
                LotterySlotMachineStage(
                    names = stageNames,
                    modifier = Modifier.fillMaxWidth(),
                    minHeight = 320.dp
                )
            }

            LotteryStageMode.Result -> {
                val winnerNames = (currentResultRound?.winners ?: session?.winners.orEmpty())
                    .map { it.displayName() }
                LotteryWinnerStage(
                    roundLabel = currentResultRound?.roundOrderLabel() ?: displayRound?.roundOrderLabel(),
                    title = currentResultRound?.title ?: displayRound?.title ?: "本轮结果",
                    winnerNames = winnerNames,
                    modifier = Modifier.fillMaxWidth(),
                    minHeight = 320.dp
                )
            }

            LotteryStageMode.Idle -> {
                LotteryStagePlaceholder(
                    roundLabel = displayRound?.roundOrderLabel(),
                    title = displayRound?.title ?: "等待主持人开始抽签",
                    description = headerState?.supportingText ?: "当前暂无抽签会话，请等待主持人设置轮次。",
                    modifier = Modifier.fillMaxWidth(),
                    minHeight = 320.dp
                )
            }
        }

        if (!actionError.isNullOrBlank()) {
            LotteryInlineErrorCard(message = actionError)
        }

        actionState?.let { state ->
            LotteryActionBar(
                message = "抽签开始后，无法退出或加入。",
                primaryLabel = state.primaryLabel,
                primaryEnabled = state.primaryEnabled,
                secondaryLabel = state.secondaryLabel,
                secondaryEnabled = state.secondaryEnabled,
                actionInProgress = actionInProgress,
                onPrimaryClick = onJoin,
                onSecondaryClick = onQuit
            )
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
    val resultRounds = when {
        session != null -> session.displayResultRounds()
        resultRound != null && resultRound.winners.isNotEmpty() -> listOf(resultRound)
        else -> emptyList()
    }

    LotteryCardFrame(modifier = modifier.fillMaxHeight()) {
        when {
            meeting == null -> {
                LotteryInlineEmptyState(
                    icon = Icons.Default.EmojiEvents,
                    title = "暂无结果",
                    description = "先选择一个可展示的抽签会议。",
                    modifier = Modifier.weight(1f, fill = true)
                )
            }

            resultRounds.isEmpty() -> {
                LotteryInlineEmptyState(
                    icon = Icons.Default.EmojiEvents,
                    title = "结果暂未产生",
                    description = "当前会议还没有可展示的中签名单。",
                    modifier = Modifier.weight(1f, fill = true)
                )
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    resultRounds.forEachIndexed { index, round ->
                        val isLatest = index == 0
                        val isCurrentResult = session?.currentResultRound()?.id == round.id

                        LotteryStatusPill(
                            text = when {
                                isCurrentResult -> "当前结果"
                                isLatest -> "最新结果"
                                else -> "已抽取"
                            },
                            tone = when {
                                isCurrentResult -> LotteryChipTone.Warning
                                isLatest -> LotteryChipTone.Primary
                                else -> LotteryChipTone.Neutral
                            }
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = round.roundOrderLabel(),
                                color = TextSecondary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = round.title,
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "抽取 ${round.winners.size} 人",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            round.winners.forEach { winner ->
                                LotteryMetaChip(
                                    text = winner.displayName(),
                                    tone = if (isLatest) LotteryChipTone.Primary else LotteryChipTone.Neutral
                                )
                            }
                        }

                        if (index != resultRounds.lastIndex) {
                            HorizontalDivider(color = Color(0xFFE7EDF5))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LotteryHistoryGroupCard(
    history: LotteryHistoryResponse,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val displayRounds = history.rounds
        .filter { it.status == "finished" || it.winners.isNotEmpty() }
        .sortedWith(
            compareByDescending<LotteryRound> { if (it.sort_order > 0) it.sort_order else Int.MIN_VALUE }
                .thenByDescending { it.id }
        )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = BorderStroke(1.dp, LotteryRecordBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .animateContentSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = history.meeting_title,
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = history.collapsedSummary(),
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LotteryMetaChip(
                        text = "${displayRounds.size} 轮",
                        tone = LotteryChipTone.Neutral
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开",
                        tint = TextSecondary
                    )
                }
            }

            if (expanded) {
                HorizontalDivider(color = Color(0xFFE7EDF5))

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
