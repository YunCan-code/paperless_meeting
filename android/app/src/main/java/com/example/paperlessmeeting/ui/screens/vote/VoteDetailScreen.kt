package com.example.paperlessmeeting.ui.screens.vote

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.paperlessmeeting.domain.model.Vote
import com.example.paperlessmeeting.domain.model.VoteOption
import com.example.paperlessmeeting.domain.model.VoteOptionResult
import com.example.paperlessmeeting.domain.model.VoteResult
import com.example.paperlessmeeting.ui.components.vote.VoteChipTone
import com.example.paperlessmeeting.ui.components.vote.VoteMetaChip
import com.example.paperlessmeeting.ui.components.vote.VoteStatusPill
import com.example.paperlessmeeting.ui.components.vote.resolveVoteStatusVisual
import com.example.paperlessmeeting.ui.theme.BackgroundGray
import com.example.paperlessmeeting.ui.theme.CardBackground
import com.example.paperlessmeeting.ui.theme.PrimaryBlue
import com.example.paperlessmeeting.ui.theme.SuccessGreen
import com.example.paperlessmeeting.ui.theme.TextPrimary
import com.example.paperlessmeeting.ui.theme.TextSecondary

private val VoteDetailBackground = Color(0xFFF3F6FB)
private val VoteMutedBorder = Color(0xFFD9E2EE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoteDetailScreen(
    navController: NavController,
    voteId: Int,
    viewModel: VoteDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(voteId) {
        viewModel.loadVote(voteId)
    }

    val currentVote = uiState.vote
    val shouldShowResults = currentVote?.status == "closed" && uiState.voteResult != null
    val shouldShowSubmitBar = currentVote?.status == "active" && !uiState.hasVoted
    val pageTitle = when {
        shouldShowResults -> "投票结果"
        currentVote?.status == "active" -> "参与投票"
        else -> "投票状态"
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = pageTitle,
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
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = VoteDetailBackground
                )
            )
        },
        bottomBar = {
            if (currentVote != null && shouldShowSubmitBar) {
                VoteSubmitBar(
                    vote = currentVote,
                    uiState = uiState,
                    onSubmit = viewModel::submitVote
                )
            }
        },
        containerColor = VoteDetailBackground
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PrimaryBlue)
                }
            }

            currentVote != null -> {
                VoteDetailContent(
                    vote = currentVote,
                    uiState = uiState,
                    shouldShowResults = shouldShowResults,
                    onOptionSelected = viewModel::toggleOption,
                    modifier = Modifier.padding(innerPadding)
                )
            }

            uiState.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.error ?: "加载失败",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VoteDetailContent(
    vote: Vote,
    uiState: VoteDetailViewModel.VoteDetailUiState,
    shouldShowResults: Boolean,
    onOptionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val sortedResults = uiState.voteResult?.results?.sortedByDescending { it.count }.orEmpty()
    val shouldShowWaitingState = uiState.hasVoted && vote.status == "active" && !shouldShowResults

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = if (vote.status == "active" && !uiState.hasVoted) 124.dp else 28.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            VoteHeroCard(vote = vote, uiState = uiState)
        }

        if (!uiState.error.isNullOrBlank()) {
            item {
                VoteInlineMessageCard(message = uiState.error)
            }
        }

        when {
            shouldShowResults -> {
                item {
                    VoteSectionHeader(
                        title = "结果概览",
                        subtitle = "共 ${uiState.voteResult?.total_voters ?: 0} 人参与"
                    )
                }

                itemsIndexed(sortedResults, key = { _, item -> item.option_id }) { index, result ->
                    VoteResultCard(
                        result = result,
                        rank = index + 1,
                        isLeader = index == 0
                    )
                }
            }

            shouldShowWaitingState -> {
                item {
                    VoteWaitingResultCard(
                        title = "已提交投票",
                        description = "你的选择已经提交成功，结果会在主持人结束投票后显示在这里。"
                    )
                }

                item {
                    VoteSectionHeader(
                        title = "本场投票选项",
                        subtitle = "当前结果暂不对移动端实时展示"
                    )
                }

                itemsIndexed(vote.options, key = { _, item -> item.id }) { index, option ->
                    VoteOptionCard(
                        option = option,
                        index = index,
                        isSelected = false,
                        enabled = false,
                        isMultiple = vote.is_multiple,
                        onClick = {}
                    )
                }
            }

            else -> {
                item {
                    VoteSelectionHintCard(vote = vote, uiState = uiState)
                }

                itemsIndexed(vote.options, key = { _, item -> item.id }) { index, option ->
                    VoteOptionCard(
                        option = option,
                        index = index,
                        isSelected = uiState.selectedOptionIds.contains(option.id),
                        enabled = vote.status == "active" && uiState.waitLeft <= 0 && !uiState.hasVoted,
                        isMultiple = vote.is_multiple,
                        onClick = { onOptionSelected(option.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VoteHeroCard(
    vote: Vote,
    uiState: VoteDetailViewModel.VoteDetailUiState
) {
    val statusVisual = resolveVoteStatusVisual(
        status = vote.status,
        hasVoted = uiState.hasVoted,
        waitLeft = uiState.waitLeft
    )
    val countdownLabel = when {
        vote.status == "closed" -> "投票已结束"
        uiState.waitLeft > 0 -> "开始倒计时 ${uiState.waitLeft}s"
        vote.status == "active" && !uiState.hasVoted && uiState.timeLeft > 0 -> "剩余 ${formatDuration(uiState.timeLeft)}"
        vote.status == "active" && uiState.hasVoted -> "已提交，等待结果公布"
        else -> "等待主持人开始"
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = CardBackground,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFF7FAFF), Color.White)
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    VoteStatusPill(visual = statusVisual)
                    VoteMetaChip(
                        text = countdownLabel,
                        tone = when {
                            vote.status == "closed" -> VoteChipTone.Neutral
                            uiState.waitLeft > 0 -> VoteChipTone.Warning
                            uiState.hasVoted -> VoteChipTone.Success
                            else -> VoteChipTone.Primary
                        },
                        leadingIcon = when {
                            vote.status == "closed" -> Icons.Default.TaskAlt
                            uiState.waitLeft > 0 -> Icons.Default.HourglassTop
                            uiState.hasVoted -> Icons.Default.TaskAlt
                            else -> Icons.Default.LockClock
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = vote.title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                if (!vote.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = vote.description,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 21.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VoteMetaChip(
                        text = if (vote.is_multiple) "多选 · 最多 ${vote.max_selections} 项" else "单选",
                        tone = VoteChipTone.Neutral
                    )
                    VoteMetaChip(
                        text = if (vote.is_anonymous) "匿名投票" else "实名投票",
                        tone = VoteChipTone.Neutral
                    )
                }
            }
        }
    }
}

@Composable
private fun VoteSelectionHintCard(
    vote: Vote,
    uiState: VoteDetailViewModel.VoteDetailUiState
) {
    val description = when {
        vote.status == "draft" -> "投票尚未开始，等待主持人开启后即可参与。"
        uiState.waitLeft > 0 -> "投票尚未开始，倒计时结束后即可选择并提交。"
        vote.is_multiple -> "请从下方选择 1 至 ${vote.max_selections} 个选项，然后在底部提交。"
        else -> "请选择你认可的一个选项，然后在底部提交投票。"
    }
    val title = if (vote.status == "draft") "等待投票开始" else "请选择投票选项"

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = CardBackground,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = title,
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun VoteWaitingResultCard(
    title: String,
    description: String
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = CardBackground,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(SuccessGreen.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.TaskAlt,
                    contentDescription = null,
                    tint = SuccessGreen
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun VoteSectionHeader(
    title: String,
    subtitle: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = TextPrimary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun VoteOptionCard(
    option: VoteOption,
    index: Int,
    isSelected: Boolean,
    enabled: Boolean,
    isMultiple: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) PrimaryBlue.copy(alpha = 0.06f) else CardBackground
    val borderColor = when {
        isSelected -> PrimaryBlue
        enabled -> VoteMutedBorder
        else -> VoteMutedBorder.copy(alpha = 0.6f)
    }
    val titleColor = when {
        isSelected -> PrimaryBlue
        enabled -> TextPrimary
        else -> TextSecondary
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        color = if (isSelected) PrimaryBlue else BackgroundGray,
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = optionLabel(index),
                    color = if (isSelected) Color.White else TextSecondary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.content,
                    color = titleColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    lineHeight = 24.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (!enabled && !isSelected) {
                        "当前不可选择"
                    } else if (isMultiple) {
                        "可多选，提交前可继续调整"
                    } else {
                        "点击即可选中该选项"
                    },
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .border(
                        width = 1.5.dp,
                        color = if (isSelected) PrimaryBlue else VoteMutedBorder,
                        shape = if (isMultiple) RoundedCornerShape(8.dp) else CircleShape
                    )
                    .background(
                        color = if (isSelected) PrimaryBlue else Color.Transparent,
                        shape = if (isMultiple) RoundedCornerShape(8.dp) else CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VoteResultCard(
    result: VoteOptionResult,
    rank: Int,
    isLeader: Boolean
) {
    val highlightColor = if (isLeader) Color(0xFFEAB308) else PrimaryBlue

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CardBackground,
        border = BorderStroke(
            width = 1.dp,
            color = if (isLeader) Color(0xFFFDE68A) else VoteMutedBorder
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(highlightColor.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = rank.toString(),
                        color = highlightColor,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = result.content,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                if (isLeader) {
                    VoteMetaChip(
                        text = "领先",
                        tone = VoteChipTone.Warning
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatPercent(result.percent),
                    color = TextPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${result.count} 票",
                    color = highlightColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { (result.percent / 100f).coerceIn(0f, 1f) },
                color = highlightColor,
                trackColor = Color(0xFFE8EEF7),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
            )
        }
    }
}

@Composable
private fun VoteInlineMessageCard(message: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFFFF7ED),
        border = BorderStroke(1.dp, Color(0xFFFED7AA)),
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

@Composable
private fun VoteSubmitBar(
    vote: Vote,
    uiState: VoteDetailViewModel.VoteDetailUiState,
    onSubmit: () -> Unit
) {
    val selectedCount = uiState.selectedOptionIds.size
    val buttonEnabled = selectedCount > 0 && uiState.waitLeft <= 0
    val helperText = when {
        uiState.waitLeft > 0 -> "投票将在 ${uiState.waitLeft}s 后开放，请稍候提交。"
        vote.is_multiple -> "已选 $selectedCount / ${vote.max_selections} 项"
        selectedCount > 0 -> "已选 1 项，确认后即可提交"
        else -> "请选择一个选项后提交"
    }

    Surface(
        color = CardBackground,
        shadowElevation = 10.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = helperText,
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onSubmit,
                enabled = buttonEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBlue,
                    disabledContainerColor = Color(0xFFCBD5E1)
                )
            ) {
                Text(
                    text = if (uiState.waitLeft > 0) "等待开始" else "提交投票",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun optionLabel(index: Int): String = ('A'.code + index).toChar().toString()

private fun formatDuration(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val minutes = safe / 60
    val remainder = safe % 60
    return "%02d:%02d".format(minutes, remainder)
}

private fun formatPercent(percent: Float): String {
    val rounded = (percent * 10).toInt()
    return if (rounded % 10 == 0) {
        "${rounded / 10}%"
    } else {
        String.format("%.1f%%", percent)
    }
}
