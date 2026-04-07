package com.example.paperlessmeeting.ui.screens.lottery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import com.example.paperlessmeeting.ui.components.lottery.LotteryCardBorderColor
import com.example.paperlessmeeting.ui.components.lottery.LotteryCardFrame
import com.example.paperlessmeeting.ui.components.lottery.LotteryChipTone
import com.example.paperlessmeeting.ui.components.lottery.LotteryEmptyStateCard
import com.example.paperlessmeeting.ui.components.lottery.LotteryHelpMenuButton
import com.example.paperlessmeeting.ui.components.lottery.LotteryInlineEmptyState
import com.example.paperlessmeeting.ui.components.lottery.LotteryMetaChip
import com.example.paperlessmeeting.ui.components.lottery.LotteryResultRoundCard
import com.example.paperlessmeeting.ui.components.lottery.LotteryRoundRecordCard
import com.example.paperlessmeeting.ui.components.lottery.LotterySectionHeader
import com.example.paperlessmeeting.ui.components.lottery.LotterySlotMachineStage
import com.example.paperlessmeeting.ui.components.lottery.LotteryStagePlaceholder
import com.example.paperlessmeeting.ui.components.lottery.LotteryStatusPill
import com.example.paperlessmeeting.ui.components.lottery.LotteryWinnerStage
import com.example.paperlessmeeting.ui.theme.PrimaryBlue
import com.example.paperlessmeeting.ui.theme.TextPrimary
import com.example.paperlessmeeting.ui.theme.TextSecondary

private val LotteryPageBackground = Color(0xFFF3F6FB)
private val LotteryRecordBorder = LotteryCardBorderColor
private val LotteryOverviewWideBreakpoint = 720.dp
private val LotteryPanelMinHeight = 320.dp
private val LotteryWideResultViewportHeight = 560.dp
internal const val LotteryWideResultListTestTag = "lottery_wide_result_list"
internal const val LotteryCurrentPanelTestTag = "lottery_current_panel"
internal const val LotteryResultPanelTestTag = "lottery_result_panel"
internal const val LotteryHeaderHelpButtonTestTag = "lottery_header_help_button"
internal const val LotteryHeaderActionButtonTestTag = "lottery_header_action_button"
private const val LotteryActionHelpText = "抽签开始后，无法退出或加入"

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
                LotteryOverviewSection(
                    uiState = uiState,
                    onJoin = viewModel::joinCurrentDisplayLottery,
                    onQuit = viewModel::quitCurrentDisplayLottery
                )
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

@Composable
internal fun LotteryOverviewSection(
    uiState: LotteryListUiState,
    onJoin: () -> Unit,
    onQuit: () -> Unit,
    modifier: Modifier = Modifier,
    forceWideLayout: Boolean? = null
) {
    var containerWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    var currentLotteryPanelHeightPx by remember { mutableIntStateOf(0) }
    val isWideLayout = forceWideLayout ?: with(density) {
        containerWidthPx.toDp() >= LotteryOverviewWideBreakpoint
    }
    val wideResultPanelHeight = with(density) {
        currentLotteryPanelHeightPx.takeIf { it > 0 }?.toDp()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { size -> containerWidthPx = size.width }
    ) {
        if (isWideLayout) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(7f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LotterySectionHeader(title = "当前抽签")
                    Box(
                        modifier = Modifier.onSizeChanged { size ->
                            currentLotteryPanelHeightPx = size.height
                        }
                    ) {
                        CurrentLotteryPanel(
                            meeting = uiState.currentDisplayMeeting,
                            session = uiState.currentDisplaySession,
                            actionError = uiState.currentActionError,
                            actionInProgress = uiState.actionInProgress,
                            onJoin = onJoin,
                            onQuit = onQuit
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(3f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LotterySectionHeader(title = "中签结果")
                    CurrentLotteryResultPanel(
                        meeting = uiState.currentDisplayMeeting,
                        resultRound = uiState.currentDisplayResultRound,
                        session = uiState.currentDisplaySession,
                        isWideLayout = true,
                        widePanelHeight = wideResultPanelHeight
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
                    onJoin = onJoin,
                    onQuit = onQuit
                )
                LotterySectionHeader(title = "中签结果")
                CurrentLotteryResultPanel(
                    meeting = uiState.currentDisplayMeeting,
                    resultRound = uiState.currentDisplayResultRound,
                    session = uiState.currentDisplaySession,
                    isWideLayout = false,
                    widePanelHeight = null
                )
            }
        }
    }
}

@Composable
private fun LotteryPanelBodyContainer(
    modifier: Modifier = Modifier,
    minHeight: Dp = LotteryPanelMinHeight,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight),
        contentAlignment = contentAlignment
    ) {
        content()
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
        LotteryCardFrame(modifier = modifier) {
            LotteryPanelBodyContainer {
                LotteryInlineEmptyState(
                    icon = Icons.Default.Schedule,
                    title = "当前暂无可展示会议",
                    description = "今天还没有可展示的抽签会话。"
                )
            }
        }
        return
    }

    val displayRound = session?.displayRound()
    val headerState = session?.headerState()
    val actionState = session?.actionState()
    val stageMode = session?.stageMode() ?: LotteryStageMode.Idle
    val stageNames = session?.slotMachineNames().orEmpty()
    val currentResultRound = session?.currentResultRound()
    val topActionLabel = actionState?.primaryLabel ?: actionState?.secondaryLabel
    val topActionEnabled = actionState?.primaryEnabled == true || actionState?.secondaryEnabled == true
    val usesPrimaryActionButton = actionState?.primaryLabel != null

    LotteryCardFrame(modifier = modifier.testTag(LotteryCurrentPanelTestTag)) {
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

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LotteryMetaChip(
                    text = headerState?.attendeeLabel ?: "待开始",
                    tone = headerState?.attendeeTone ?: LotteryChipTone.Neutral,
                    leadingIcon = if (session?.joined == true) Icons.Default.CheckCircle else Icons.Default.Schedule
                )
                LotteryHelpMenuButton(
                    helpText = LotteryActionHelpText,
                    modifier = Modifier.testTag(LotteryHeaderHelpButtonTestTag)
                )
                topActionLabel?.let { label ->
                    if (usesPrimaryActionButton) {
                        Button(
                            onClick = onJoin,
                            enabled = topActionEnabled && !actionInProgress,
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            modifier = Modifier
                                .sizeIn(minHeight = 40.dp)
                                .testTag(LotteryHeaderActionButtonTestTag)
                        ) {
                            Text(
                                text = if (actionInProgress) "处理中..." else label,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = onQuit,
                            enabled = topActionEnabled && !actionInProgress,
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                            border = BorderStroke(1.dp, Color(0xFFF4A3A3)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB42318)),
                            modifier = Modifier
                                .sizeIn(minHeight = 40.dp)
                                .testTag(LotteryHeaderActionButtonTestTag)
                        ) {
                            Text(
                                text = if (actionInProgress) "处理中..." else label,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        when (stageMode) {
            LotteryStageMode.Rolling -> {
                LotterySlotMachineStage(
                    names = stageNames,
                    modifier = Modifier.fillMaxWidth(),
                    minHeight = LotteryPanelMinHeight
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
                    minHeight = LotteryPanelMinHeight
                )
            }

            LotteryStageMode.Idle -> {
                LotteryStagePlaceholder(
                    roundLabel = displayRound?.roundOrderLabel(),
                    title = displayRound?.title ?: "等待主持人开始抽签",
                    description = headerState?.supportingText ?: "当前暂无抽签会话，请等待主持人设置轮次。",
                    modifier = Modifier.fillMaxWidth(),
                    minHeight = LotteryPanelMinHeight
                )
            }
        }

        if (!actionError.isNullOrBlank()) {
            LotteryInlineErrorCard(message = actionError)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CurrentLotteryResultPanel(
    meeting: Meeting?,
    resultRound: LotteryRound?,
    session: LotterySession?,
    isWideLayout: Boolean,
    widePanelHeight: Dp?,
    modifier: Modifier = Modifier
) {
    val resultRounds = when {
        session != null -> session.displayResultRounds()
        resultRound != null && resultRound.winners.isNotEmpty() -> listOf(resultRound)
        else -> emptyList()
    }
    val latestRoundId = resultRounds.firstOrNull()?.id
    val resolvedWidePanelHeight = (widePanelHeight ?: LotteryWideResultViewportHeight)
        .coerceAtLeast(LotteryPanelMinHeight)
    val resolvedModifier = if (isWideLayout) {
        modifier
            .height(resolvedWidePanelHeight)
            .testTag(LotteryResultPanelTestTag)
    } else {
        modifier.testTag(LotteryResultPanelTestTag)
    }

    LotteryCardFrame(modifier = resolvedModifier) {
        when {
            meeting == null -> {
                LotteryPanelBodyContainer(
                    modifier = if (isWideLayout) Modifier.weight(1f, fill = true) else Modifier
                ) {
                    LotteryInlineEmptyState(
                        icon = Icons.Default.EmojiEvents,
                        title = "暂无结果",
                        description = "先选择一个可展示的抽签会议。"
                    )
                }
            }

            resultRounds.isEmpty() -> {
                LotteryPanelBodyContainer(
                    modifier = if (isWideLayout) Modifier.weight(1f, fill = true) else Modifier
                ) {
                    LotteryInlineEmptyState(
                        icon = Icons.Default.EmojiEvents,
                        title = "结果暂未产生",
                        description = "当前会议还没有可展示的中签名单。"
                    )
                }
            }

            else -> {
                if (isWideLayout) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag(LotteryWideResultListTestTag),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(resultRounds, key = { it.id }) { round ->
                                val isLatest = latestRoundId == round.id
                                val isCurrentResult = session?.currentResultRound()?.id == round.id
                                val statusLabel = when {
                                    isCurrentResult -> "当前结果"
                                    isLatest -> "最新结果"
                                    else -> "已抽取"
                                }
                                val statusTone = when {
                                    isCurrentResult -> LotteryChipTone.Warning
                                    isLatest -> LotteryChipTone.Primary
                                    else -> LotteryChipTone.Neutral
                                }

                                LotteryResultRoundCard(
                                    roundLabel = round.roundOrderLabel(),
                                    title = round.title,
                                    statusLabel = statusLabel,
                                    statusTone = statusTone,
                                    drawCount = round.winners.size,
                                    winnerNames = round.winners.map { it.displayName() },
                                    highlighted = isCurrentResult || isLatest
                                )
                            }
                        }
                    }
                } else {
                    LotteryPanelBodyContainer(contentAlignment = Alignment.TopStart) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            resultRounds.forEach { round ->
                                val isLatest = latestRoundId == round.id
                                val isCurrentResult = session?.currentResultRound()?.id == round.id
                                val statusLabel = when {
                                    isCurrentResult -> "当前结果"
                                    isLatest -> "最新结果"
                                    else -> "已抽取"
                                }
                                val statusTone = when {
                                    isCurrentResult -> LotteryChipTone.Warning
                                    isLatest -> LotteryChipTone.Primary
                                    else -> LotteryChipTone.Neutral
                                }

                                LotteryResultRoundCard(
                                    roundLabel = round.roundOrderLabel(),
                                    title = round.title,
                                    statusLabel = statusLabel,
                                    statusTone = statusTone,
                                    drawCount = round.winners.size,
                                    winnerNames = round.winners.map { it.displayName() },
                                    highlighted = isCurrentResult || isLatest
                                )
                            }
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
