package com.example.paperlessmeeting.ui.screens.vote

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Refresh
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
import com.example.paperlessmeeting.domain.model.VoteOptionResult
import com.example.paperlessmeeting.domain.model.VoteResult
import com.example.paperlessmeeting.ui.components.vote.VoteChipTone
import com.example.paperlessmeeting.ui.components.vote.VoteEmptyStateCard
import com.example.paperlessmeeting.ui.components.vote.VoteMetaChip
import com.example.paperlessmeeting.ui.components.vote.VoteStatusPill
import com.example.paperlessmeeting.ui.components.vote.resolveVoteStatusVisual
import com.example.paperlessmeeting.ui.theme.CardBackground
import com.example.paperlessmeeting.ui.theme.PrimaryBlue
import com.example.paperlessmeeting.ui.theme.TextPrimary
import com.example.paperlessmeeting.ui.theme.TextSecondary

private val VotePageBackground = Color(0xFFF3F6FB)
private val VoteCardBorder = Color(0xFFDCE4F0)
private val VoteRecordBorder = Color(0xFFE4EAF3)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoteListScreen(
    navController: NavController,
    viewModel: VoteListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "投票",
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
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = VotePageBackground)
            )
        },
        containerColor = VotePageBackground
    ) { innerPadding ->
        if (uiState.isLoading && uiState.currentDisplayVote == null && uiState.myVoteHistory.isEmpty()) {
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
                    VoteInlineErrorCard(message = uiState.error ?: "加载失败")
                }
            }

            item {
                VoteSectionHeader(
                    title = "当前投票"
                )
            }

            item {
                uiState.currentDisplayVote?.let { vote ->
                    CurrentVoteSpotlightCard(
                        vote = vote,
                        onClick = { navController.navigate("vote_detail/${vote.id}") }
                    )
                } ?: VoteEmptyStateCard(
                    icon = Icons.Default.HowToVote,
                    title = "当前暂无可展示的投票"
                )
            }

            item {
                VoteSectionHeader(
                    title = "我的投票记录"
                )
            }

            if (uiState.myVoteHistory.isEmpty()) {
                item {
                    VoteEmptyStateCard(
                        icon = Icons.Default.History,
                        title = "还没有参与记录",
                        description = "参与过投票后，记录会沉淀在这里，方便你回看结果。"
                    )
                }
            } else {
                items(uiState.myVoteHistory, key = { it.id }) { vote ->
                    VoteRecordCard(
                        vote = vote,
                        expanded = uiState.expandedHistoryVoteId == vote.id,
                        voteResult = uiState.historyVoteResults[vote.id],
                        loadingResult = vote.id in uiState.loadingHistoryResultIds,
                        resultError = uiState.historyResultErrors[vote.id],
                        onClick = { viewModel.toggleHistoryVote(vote.id) },
                        onRetryLoadResult = { viewModel.retryHistoryVoteResult(vote.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VoteSectionHeader(title: String, subtitle: String? = null) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = TextPrimary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        subtitle?.takeIf { it.isNotBlank() }?.let {
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = it,
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 21.sp
            )
        }
    }
}

@Composable
private fun CurrentVoteSpotlightCard(vote: Vote, onClick: () -> Unit) {
    val statusVisual = resolveVoteStatusVisual(vote.status, vote.user_voted, vote.wait_seconds ?: 0)

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = CardBackground,
        border = BorderStroke(1.dp, VoteCardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
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
                .padding(22.dp)
        ) {
            Surface(
                color = PrimaryBlue.copy(alpha = 0.08f),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(84.dp)
            ) {}

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VoteStatusPill(visual = statusVisual)
                    VoteMetaChip(text = if (vote.is_multiple) "多选" else "单选", tone = VoteChipTone.Neutral)
                    VoteMetaChip(text = if (vote.is_anonymous) "匿名" else "实名", tone = VoteChipTone.Neutral)
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = vote.title,
                        color = TextPrimary,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    vote.description?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    voteTimeChip(vote)?.let {
                        VoteMetaChip(
                            text = it,
                            tone = VoteChipTone.Warning,
                            leadingIcon = Icons.Default.AccessTime
                        )
                    }
                    if (vote.user_voted) {
                        VoteMetaChip(text = "已参与", tone = VoteChipTone.Success)
                    }
                    if (vote.is_multiple) {
                        VoteMetaChip(text = "最多 ${vote.max_selections} 项", tone = VoteChipTone.Neutral)
                    }
                }

                Button(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text(
                        text = currentVoteActionLabel(vote),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun VoteRecordCard(
    vote: Vote,
    expanded: Boolean,
    voteResult: VoteResult?,
    loadingResult: Boolean,
    resultError: String?,
    onClick: () -> Unit,
    onRetryLoadResult: () -> Unit
) {
    val statusVisual = resolveVoteStatusVisual(vote.status, vote.user_voted, vote.wait_seconds ?: 0)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CardBackground,
        border = BorderStroke(1.dp, VoteRecordBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(
                            if (vote.status == "closed") Color(0xFFE2E8F0) else Color(0xFFEAF2FF),
                            RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (vote.status == "closed") Icons.Default.History else Icons.Default.HowToVote,
                        contentDescription = null,
                        tint = if (vote.status == "closed") Color(0xFF64748B) else PrimaryBlue
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VoteStatusPill(visual = statusVisual)
                        if (vote.user_voted) {
                            VoteMetaChip(text = "已参与", tone = VoteChipTone.Success)
                        }
                    }
                    Spacer(modifier = Modifier.size(10.dp))
                    Text(
                        text = vote.title,
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = formatVoteDate(vote.started_at ?: vote.created_at),
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (expanded) "收起结果" else "查看结果",
                        color = PrimaryBlue,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = TextSecondary.copy(alpha = 0.82f)
                    )
                }
            }

            if (expanded) {
                HorizontalDivider(color = VoteRecordBorder)

                VoteRecordResultSection(
                    vote = vote,
                    voteResult = voteResult,
                    loadingResult = loadingResult,
                    resultError = resultError,
                    onRetryLoadResult = onRetryLoadResult
                )
            }
        }
    }
}

@Composable
private fun VoteRecordResultSection(
    vote: Vote,
    voteResult: VoteResult?,
    loadingResult: Boolean,
    resultError: String?,
    onRetryLoadResult: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "投票结果",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "共 ${voteResult?.total_voters ?: 0} 人参与",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }

        when {
            loadingResult -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = PrimaryBlue
                    )
                    Text(
                        text = "正在加载结果...",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            !resultError.isNullOrBlank() -> {
                VoteInlineErrorCard(message = resultError)
                Button(
                    onClick = onRetryLoadResult,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("重试加载", fontWeight = FontWeight.Bold)
                }
            }

            voteResult != null && voteResult.results.isNotEmpty() -> {
                voteResult.results
                    .sortedByDescending { it.count }
                    .forEachIndexed { index, result ->
                        VoteRecordResultRow(
                            result = result,
                            rank = index + 1,
                            isLeader = index == 0
                        )
                    }
            }

            else -> {
                VoteEmptyStateCard(
                    icon = Icons.Default.History,
                    title = if (vote.status == "closed") "结果暂未生成" else "结果待公布",
                    description = if (vote.status == "closed") {
                        "这条投票记录暂时没有可展示的统计结果。"
                    } else {
                        "主持人结束投票后，结果会展示在这里。"
                    }
                )
            }
        }
    }
}

@Composable
private fun VoteRecordResultRow(
    result: VoteOptionResult,
    rank: Int,
    isLeader: Boolean
) {
    val highlightColor = if (isLeader) Color(0xFFEAB308) else PrimaryBlue

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFF8FAFD),
        border = BorderStroke(1.dp, if (isLeader) Color(0xFFFDE68A) else VoteRecordBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
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

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = result.content,
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = "${result.count} 票",
                    color = highlightColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = formatPercent(result.percent),
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )

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
private fun VoteInlineErrorCard(message: String) {
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

private fun voteTimeChip(vote: Vote): String? {
    val waitLeft = vote.wait_seconds ?: 0
    return when {
        vote.status == "draft" -> "待主持人开始"
        waitLeft > 0 -> "倒计时 ${waitLeft}s"
        vote.status == "active" && (vote.remaining_seconds ?: 0) > 0 -> "剩余 ${formatDuration(vote.remaining_seconds ?: 0)}"
        else -> null
    }
}

private fun currentVoteActionLabel(vote: Vote): String {
    return when {
        vote.status == "draft" -> "查看投票"
        (vote.wait_seconds ?: 0) > 0 -> "查看状态"
        vote.user_voted -> "查看投票状态"
        else -> "进入投票"
    }
}

private fun formatVoteDate(value: String?): String {
    if (value.isNullOrBlank()) return "时间未设置"
    return value.replace("T", " ").substringBefore(".").let {
        if (it.length >= 16) it.substring(0, 16) else it
    }
}

private fun formatDuration(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return "%02d:%02d".format(safe / 60, safe % 60)
}

private fun formatPercent(percent: Float): String {
    val rounded = (percent * 10).toInt()
    return if (rounded % 10 == 0) {
        "${rounded / 10}%"
    } else {
        String.format("%.1f%%", percent)
    }
}
