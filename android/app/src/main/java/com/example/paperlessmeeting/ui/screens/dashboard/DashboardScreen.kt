package com.example.paperlessmeeting.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.paperlessmeeting.domain.model.Meeting
import com.example.paperlessmeeting.domain.model.MeetingStatus
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onMeetingClick: (Int) -> Unit,
    onReadingClick: (String, String, Int) -> Unit = { _, _, _ -> }, // url, name, page
    onLotteryClick: () -> Unit = {},
    onVoteClick: () -> Unit = {},
    onCheckInClick: () -> Unit = {},
    viewModel: DashboardViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Refresh data
    LaunchedEffect(Unit) {
        viewModel.refreshData()
    }
    
    // Toast Handling
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Vote List Sheet Logic - REMOVED for clean full screen navigation

    when (val state = uiState) {
        is DashboardUiState.Loading -> {
             Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                 CircularProgressIndicator()
             }
        }
        is DashboardUiState.Error -> {
             Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                 Text("API Error: ${state.message}", color = MaterialTheme.colorScheme.error)
             }
        }
        is DashboardUiState.Success -> {
            DashboardContent(
                state = state, 
                onMeetingClick = onMeetingClick, 
                onReadingClick = onReadingClick,
                onVoteClick = onVoteClick,
                onLotteryClick = onLotteryClick,
                onCheckInClick = onCheckInClick
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DashboardContent(
    state: DashboardUiState.Success, 
    onMeetingClick: (Int) -> Unit, 
    onReadingClick: (String, String, Int) -> Unit,
    onVoteClick: () -> Unit,
    onLotteryClick: () -> Unit,
    onCheckInClick: () -> Unit
) {
    // Debug log
    android.util.Log.d("DashboardDebug", "Active Meetings Count: ${state.activeMeetings.size}")
    state.activeMeetings.forEach { 
        android.util.Log.d("DashboardDebug", "Meeting: ${it.title}, TypeName: ${it.meetingTypeName}")
    }

    val currentHour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greeting = when (currentHour) {
        in 5..11 -> "上午好"
        in 12..13 -> "中午好"
        in 14..18 -> "下午好"
        else -> "晚上好"
    }
    
    val currentDate = remember { 
        val now = java.time.LocalDate.now()
        now.format(DateTimeFormatter.ofPattern("yy年MM月dd日 EEEE", Locale.CHINA))
    }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val isPhone = screenWidthDp < 600
    val contentPadding = if (isPhone) 16.dp else 24.dp
    val heroCardHeight = if (isPhone) 160.dp else 200.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
    ) {
        // 1. Header
        Text(
            text = "$greeting, ${state.userName}",
            style = if (isPhone) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "今天是 $currentDate",
            style = if (isPhone) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(if (isPhone) 20.dp else 32.dp))

        // 2. Hero Card (Up Next)
        Text(
            text = "今日会议",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        if (state.activeMeetings.isNotEmpty()) {
            val actualCount = state.activeMeetings.size
            val loopMultiplier = 1000
            val virtualPageCount = if (actualCount > 1) actualCount * loopMultiplier else 1
            val startPage = if (actualCount > 1) (virtualPageCount / 2) - ((virtualPageCount / 2) % actualCount) + state.initialPageIndex else 0
            
            val pagerState = rememberPagerState(
                initialPage = startPage,
                pageCount = { virtualPageCount }
            )
            
            androidx.compose.runtime.LaunchedEffect(state.initialPageIndex) {
                if (state.activeMeetings.isNotEmpty() && actualCount > 1) {
                    val targetPage = (virtualPageCount / 2) - ((virtualPageCount / 2) % actualCount) + state.initialPageIndex
                    pagerState.scrollToPage(targetPage)
                }
            }
            
            Column {
                HorizontalPager(
                    state = pagerState,
                    pageSpacing = if (isPhone) 12.dp else 16.dp,
                    modifier = Modifier.fillMaxWidth().height(heroCardHeight) 
                ) { virtualPage ->
                    // 取模映射到实际索引
                    val actualPage = virtualPage % actualCount
                    val meeting = state.activeMeetings[actualPage]
                    com.example.paperlessmeeting.ui.components.MeetingCard(
                        meeting = meeting,
                        statusOverride = resolveTodayMeetingStatus(state.activeMeetings, actualPage),
                        onClick = { onMeetingClick(meeting.id) }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Indicators
                if (actualCount > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(actualCount) { iteration ->
                            val currentActualPage = pagerState.currentPage % actualCount
                            val color = if (currentActualPage == iteration) 
                                MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.surfaceVariant
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .size(8.dp)
                            )
                        }
                    }
                }
            }
        } else {
             Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isPhone) 120.dp else 160.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f),
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.1f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
             ) {
                 Column(horizontalAlignment = Alignment.CenterHorizontally) {
                     Icon(
                         imageVector = Icons.Default.EventAvailable, // Or similar icon
                         contentDescription = null,
                         tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                         modifier = Modifier.size(48.dp)
                     )
                     Spacer(modifier = Modifier.height(12.dp))
                     Text(
                         text = "今日暂无会议安排", 
                         style = MaterialTheme.typography.titleMedium,
                         color = MaterialTheme.colorScheme.onSurface,
                         fontWeight = FontWeight.Medium
                     )
                 }
             }
        }

        Spacer(modifier = Modifier.height(if (isPhone) 16.dp else 24.dp))

        // Quick Actions Card
        Text(
            text = "快捷功能",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                QuickActionButton(
                    icon = Icons.Default.HowToVote,
                    label = "投票",
                    onClick = onVoteClick
                )

                QuickActionButton(
                    icon = Icons.Default.Refresh,
                    label = "抽签",
                    onClick = onLotteryClick
                )

                /* 暂时隐藏打卡按钮
                QuickActionButton(
                    icon = Icons.Default.Edit,
                    label = "打卡",
                    onClick = onCheckInClick
                )
                */
            }
        }

        Spacer(modifier = Modifier.height(if (isPhone) 20.dp else 32.dp))

        // 3. Recent Reading (Using reading progress)
        Text(
            text = "最近阅读",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        if (state.readingProgress.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(state.readingProgress) { progress ->
                    com.example.paperlessmeeting.ui.components.RecentReadingCard(
                        progress = progress,
                        onClick = {
                            onReadingClick(progress.uniqueId, progress.fileName, progress.currentPage)
                        }
                    )
                }
            }
        } else {
            // 空状态提示
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "暂无阅读记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(80.dp))
    }
}




private fun resolveTodayMeetingStatus(
    meetings: List<Meeting>,
    index: Int,
    now: LocalDateTime = LocalDateTime.now()
): MeetingStatus {
    if (index !in meetings.indices) return MeetingStatus.Draft
    val meeting = meetings[index]
    val start = parseMeetingDateTime(meeting.startTime) ?: return meeting.getUiStatus()
    val endExplicit = parseMeetingDateTime(meeting.endTime)

    val inferredEnd = if (endExplicit != null) {
        endExplicit
    } else {
        val nextStart = meetings
            .drop(index + 1)
            .firstNotNullOfOrNull { parseMeetingDateTime(it.startTime) }
        if (nextStart != null) {
            nextStart.minusMinutes(15)
        } else {
            start.toLocalDate().plusDays(1).atStartOfDay()
        }
    }

    val effectiveEnd = if (inferredEnd.isAfter(start)) inferredEnd else start

    return when {
        now.isBefore(start) -> MeetingStatus.Upcoming
        !now.isBefore(effectiveEnd) -> MeetingStatus.Finished
        else -> MeetingStatus.Ongoing
    }
}

private fun parseMeetingDateTime(raw: String?): LocalDateTime? {
    if (raw.isNullOrBlank()) return null
    val normalized = raw.replace(" ", "T")
    return try {
        LocalDateTime.parse(normalized)
    } catch (_: Exception) {
        try {
            OffsetDateTime.parse(normalized).toLocalDateTime()
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
fun RecentFileCard(file: com.example.paperlessmeeting.domain.model.Attachment) {
    Card(
        modifier = Modifier
            .width(150.dp)
            .height(190.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Placeholder Thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                 // Logic to show different file types
                 val ext = if (file.filename.contains(".")) file.filename.substringAfterLast(".").uppercase() else "FILE"
                 
                 Text(
                     text = ext, 
                     modifier = Modifier.align(Alignment.Center),
                     color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                     fontWeight = FontWeight.Bold
                 )
            }
            // Metadata
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = file.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(4.dp))
                
                // Helper to format bytes
                val sizeStr = if (file.fileSize > 1024 * 1024) "${file.fileSize / 1024 / 1024} MB" else "${file.fileSize / 1024} KB"
                
                Text(
                    text = sizeStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val isPhone = LocalConfiguration.current.screenWidthDp < 600
    val btnSize = if (isPhone) 48.dp else 56.dp
    val iconSize = if (isPhone) 24.dp else 28.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(8.dp)
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(btnSize),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
