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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.paperlessmeeting.domain.model.Meeting
import com.example.paperlessmeeting.domain.model.MeetingStatus
import com.example.paperlessmeeting.domain.model.Vote
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
private val VoteSoftBlue = Color(0xFFEAF2FF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoteListScreen(
    navController: NavController,
    viewModel: VoteListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showMeetingSheet by remember { mutableStateOf(false) }
    val crossMeetingHistory = remember(uiState.globalHistoryVotes, uiState.selectedMeetingId) {
        uiState.globalHistoryVotes.filter { it.meeting_id != uiState.selectedMeetingId }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "会议互动中心",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新", tint = PrimaryBlue)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = VotePageBackground)
            )
        },
        containerColor = VotePageBackground
    ) { innerPadding ->
        when {
            uiState.isLoading && uiState.todayMeetings.isEmpty() && uiState.globalHistoryVotes.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PrimaryBlue)
                }
            }

            uiState.selectedMeeting == null && uiState.globalHistoryVotes.isEmpty() -> {
                EmptyVoteHome(
                    innerPadding = innerPadding,
                    error = uiState.error
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 112.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        VoteIntroCard(
                            title = if (uiState.selectedMeeting != null) "当前会议互动" else "历史互动",
                            subtitle = if (uiState.selectedMeeting != null) {
                                "先看当前会议，再进入具体投票与结果，移动端与后台互动中心保持同一条主链路。"
                            } else {
                                "今天暂无会议，先回看最近参与过的投票结果。"
                            }
                        )
                    }

                    if (!uiState.error.isNullOrBlank()) {
                        item { VoteInlineErrorCard(message = uiState.error ?: "加载失败") }
                    }

                    uiState.selectedMeeting?.let { meeting ->
                        item {
                            MeetingContextCard(
                                meeting = meeting,
                                totalMeetings = uiState.todayMeetings.size,
                                activeCount = uiState.selectedMeetingActiveVotes.size,
                                historyCount = uiState.selectedMeetingHistoryVotes.size,
                                onSwitchMeeting = { showMeetingSheet = true }
                            )
                        }

                        item {
                            VoteSectionHeader(
                                title = "当前互动",
                                subtitle = "优先展示这场会议里正在进行或即将开放的投票。"
                            )
                        }

                        if (uiState.selectedMeetingActiveVotes.isEmpty()) {
                            item {
                                VoteEmptyStateCard(
                                    icon = Icons.Default.HowToVote,
                                    title = "本场暂无进行中的投票",
                                    description = "新的投票开始后，会优先出现在这里。"
                                )
                            }
                        } else {
                            items(uiState.selectedMeetingActiveVotes, key = { it.id }) { vote ->
                                CurrentVoteCard(
                                    vote = vote,
                                    onClick = { navController.navigate("vote_detail/${vote.id}") }
                                )
                            }
                        }

                        item {
                            VoteSectionHeader(
                                title = "本场记录",
                                subtitle = "查看当前会议已结束的投票结果和本场互动沉淀。"
                            )
                        }

                        if (uiState.selectedMeetingHistoryVotes.isEmpty()) {
                            item {
                                VoteEmptyStateCard(
                                    icon = Icons.Default.History,
                                    title = "本场还没有已结束投票",
                                    description = "投票关闭后，结果入口会沉淀到这里。"
                                )
                            }
                        } else {
                            items(uiState.selectedMeetingHistoryVotes, key = { it.id }) { vote ->
                                VoteRecordCard(
                                    vote = vote,
                                    onClick = { navController.navigate("vote_detail/${vote.id}") }
                                )
                            }
                        }

                        if (crossMeetingHistory.isNotEmpty()) {
                            item {
                                VoteSectionHeader(
                                    title = "我的历史记录",
                                    subtitle = "这里沉淀跨会议的近期投票结果。"
                                )
                            }
                            items(crossMeetingHistory.take(6), key = { it.id }) { vote ->
                                VoteRecordCard(
                                    vote = vote,
                                    onClick = { navController.navigate("vote_detail/${vote.id}") },
                                    compact = true
                                )
                            }
                        }
                    } ?: run {
                        item {
                            VoteSectionHeader(
                                title = "我的历史记录",
                                subtitle = "今天暂无会议，先查看你最近参与过的投票。"
                            )
                        }
                        items(uiState.globalHistoryVotes, key = { it.id }) { vote ->
                            VoteRecordCard(
                                vote = vote,
                                onClick = { navController.navigate("vote_detail/${vote.id}") }
                            )
                        }
                    }
                }
            }
        }

        if (showMeetingSheet && uiState.todayMeetings.isNotEmpty()) {
            MeetingSwitchBottomSheet(
                meetings = uiState.todayMeetings,
                selectedMeetingId = uiState.selectedMeetingId,
                onSelectMeeting = {
                    viewModel.selectMeeting(it)
                    showMeetingSheet = false
                },
                onDismiss = { showMeetingSheet = false }
            )
        }
    }
}

@Composable
private fun EmptyVoteHome(
    innerPadding: PaddingValues,
    error: String?
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            VoteIntroCard(
                title = "移动端会议互动",
                subtitle = "先看当前会议，再进入具体投票和结果，和后台互动中心保持同一条主链路。"
            )
        }
        if (!error.isNullOrBlank()) {
            item { VoteInlineErrorCard(message = error) }
        }
        item {
            VoteEmptyStateCard(
                icon = Icons.Default.HowToVote,
                title = "今天还没有可展示的投票互动",
                description = "当日会议开始后，当前会议的投票会优先出现在这里。"
            )
        }
    }
}

@Composable
private fun VoteIntroCard(title: String, subtitle: String) {
    Surface(shape = RoundedCornerShape(26.dp), color = CardBackground, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .background(Brush.linearGradient(listOf(Color(0xFFF7FAFF), Color(0xFFEAF2FF))))
                .padding(20.dp)
        ) {
            Surface(
                color = PrimaryBlue.copy(alpha = 0.08f),
                shape = CircleShape,
                modifier = Modifier.align(Alignment.TopEnd).size(74.dp)
            ) {}

            Column(modifier = Modifier.fillMaxWidth()) {
                VoteMetaChip(text = "投票中心", tone = VoteChipTone.Primary)
                Spacer(modifier = Modifier.height(14.dp))
                Text(text = title, color = TextPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = subtitle, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, lineHeight = 22.sp)
            }
        }
    }
}

@Composable
private fun MeetingContextCard(
    meeting: Meeting,
    totalMeetings: Int,
    activeCount: Int,
    historyCount: Int,
    onSwitchMeeting: () -> Unit
) {
    val meetingStatus = meeting.getUiStatus()
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = CardBackground,
        border = BorderStroke(1.dp, VoteCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        VoteMetaChip(
                            text = meetingStatusLabel(meetingStatus),
                            tone = when (meetingStatus) {
                                MeetingStatus.Ongoing -> VoteChipTone.Primary
                                MeetingStatus.Upcoming -> VoteChipTone.Warning
                                MeetingStatus.Finished -> VoteChipTone.Neutral
                                MeetingStatus.Draft -> VoteChipTone.Danger
                            }
                        )
                        VoteMetaChip(text = "今日 $totalMeetings 场会议", tone = VoteChipTone.Neutral)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = meeting.title,
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = formatMeetingRange(meeting), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }

                if (totalMeetings > 1) {
                    Button(
                        onClick = onSwitchMeeting,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VoteSoftBlue, contentColor = PrimaryBlue),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(text = "切换会议", fontWeight = FontWeight.Bold)
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }

            HorizontalDivider(color = VoteCardBorder)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MeetingMetricCard(
                    modifier = Modifier.weight(1f),
                    label = "当前互动",
                    value = activeCount.toString(),
                    description = if (activeCount > 0) "可直接进入参与" else "本场暂无活动投票"
                )
                MeetingMetricCard(
                    modifier = Modifier.weight(1f),
                    label = "本场记录",
                    value = historyCount.toString(),
                    description = if (historyCount > 0) "已结束投票沉淀在这里" else "结果沉淀会出现在这里"
                )
            }
        }
    }
}

@Composable
private fun MeetingMetricCard(label: String, value: String, description: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(18.dp), color = VoteSoftBlue, modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = label, color = TextSecondary, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = value, color = TextPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = description, color = TextSecondary, style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun VoteSectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = subtitle, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CurrentVoteCard(vote: Vote, onClick: () -> Unit) {
    val statusVisual = resolveVoteStatusVisual(vote.status, vote.user_voted, vote.wait_seconds ?: 0)
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = CardBackground,
        border = BorderStroke(1.dp, VoteCardBorder),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        VoteStatusPill(visual = statusVisual)
                        VoteMetaChip(text = if (vote.is_multiple) "多选" else "单选", tone = VoteChipTone.Neutral)
                        VoteMetaChip(text = if (vote.is_anonymous) "匿名" else "实名", tone = VoteChipTone.Neutral)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = vote.title,
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = TextSecondary.copy(alpha = 0.8f))
            }

            vote.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 21.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                voteTimeChip(vote)?.let {
                    VoteMetaChip(text = it, tone = VoteChipTone.Warning, leadingIcon = Icons.Default.AccessTime)
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
                    text = when {
                        vote.user_voted && vote.status == "active" -> "查看状态"
                        vote.status == "closed" -> "查看结果"
                        else -> "进入投票"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun VoteRecordCard(vote: Vote, onClick: () -> Unit, compact: Boolean = false) {
    val statusVisual = resolveVoteStatusVisual(vote.status, vote.user_voted, vote.wait_seconds ?: 0)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CardBackground,
        border = BorderStroke(1.dp, VoteRecordBorder),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(horizontal = 18.dp, vertical = if (compact) 14.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(if (compact) 42.dp else 46.dp)
                    .background(if (vote.status == "closed") Color(0xFFE2E8F0) else VoteSoftBlue, RoundedCornerShape(14.dp)),
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    VoteStatusPill(visual = statusVisual)
                    if (vote.user_voted) {
                        VoteMetaChip(text = "已参与", tone = VoteChipTone.Success)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = vote.title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = formatVoteDate(vote.started_at ?: vote.created_at), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (vote.status == "closed") "查看结果" else "进入投票",
                color = PrimaryBlue,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeetingSwitchBottomSheet(
    meetings: List<Meeting>,
    selectedMeetingId: Int?,
    onSelectMeeting: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = VotePageBackground) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(text = "切换当前会议", color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = "选择后，当前互动和本场记录会一起切换。", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(18.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
                items(meetings, key = { it.id }) { meeting ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = CardBackground,
                        border = BorderStroke(1.dp, if (meeting.id == selectedMeetingId) PrimaryBlue.copy(alpha = 0.32f) else VoteCardBorder),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { onSelectMeeting(meeting.id) }
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = meeting.title,
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (meeting.id == selectedMeetingId) {
                                    VoteMetaChip(text = "当前查看", tone = VoteChipTone.Primary)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = formatMeetingRange(meeting), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(12.dp))
                            VoteMetaChip(text = meetingStatusLabel(meeting.getUiStatus()), tone = VoteChipTone.Neutral)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoteInlineErrorCard(message: String) {
    Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFFFF7ED), modifier = Modifier.fillMaxWidth()) {
        Text(text = message, color = Color(0xFF9A3412), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp))
    }
}

private fun voteTimeChip(vote: Vote): String? {
    val waitLeft = vote.wait_seconds ?: 0
    return when {
        waitLeft > 0 -> "倒计时 ${waitLeft}s"
        vote.status == "active" && (vote.remaining_seconds ?: 0) > 0 -> "剩余 ${formatDuration(vote.remaining_seconds ?: 0)}"
        else -> null
    }
}

private fun meetingStatusLabel(status: MeetingStatus): String {
    return when (status) {
        MeetingStatus.Ongoing -> "进行中"
        MeetingStatus.Upcoming -> "即将开始"
        MeetingStatus.Finished -> "已结束"
        MeetingStatus.Draft -> "草稿"
    }
}

private fun formatMeetingRange(meeting: Meeting): String {
    val start = formatVoteDate(meeting.startTime)
    val end = formatVoteDate(meeting.endTime)
    return if (end == "时间未设置") start else "$start - $end"
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
