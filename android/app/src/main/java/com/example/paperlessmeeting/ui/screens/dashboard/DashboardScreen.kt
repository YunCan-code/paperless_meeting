package com.example.paperlessmeeting.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.paperlessmeeting.domain.model.Meeting
import com.example.paperlessmeeting.domain.model.MeetingStatus
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

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
                onDeleteReading = viewModel::deleteReadingProgresses,
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
    onDeleteReading: (List<String>) -> Unit,
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
        in 5..11 -> "\u4e0a\u5348\u597d"
        in 12..13 -> "\u4e2d\u5348\u597d"
        in 14..18 -> "\u4e0b\u5348\u597d"
        else -> "\u665a\u4e0a\u597d"
    }
    
    val currentDate = remember { 
        val now = java.time.LocalDate.now()
        now.format(DateTimeFormatter.ofPattern("yyyy\u5e74M\u6708d\u65e5 EEEE", Locale.CHINA)) // yyyy年
    }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val isPhone = screenWidthDp < 600
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val undoProgress = remember { Animatable(0f) }
    var selectedReadingIds by rememberSaveable(state.readingProgress) { mutableStateOf(listOf<String>()) }
    var pendingDeleteProgresses by remember { mutableStateOf<List<com.example.paperlessmeeting.data.local.ReadingProgress>>(emptyList()) }

    val contentPadding = if (isPhone) 16.dp else 24.dp
    val heroCardHeight = if (isPhone) 160.dp else 200.dp
    val isSelectionMode = selectedReadingIds.isNotEmpty()
    val pendingDeleteIds = pendingDeleteProgresses.map { it.uniqueId }.toSet()
    val selectedProgress = state.readingProgress.filter { it.uniqueId in selectedReadingIds }
    val visibleReadingProgress = state.readingProgress.filterNot { it.uniqueId in pendingDeleteIds }
    val undoBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + if (isPhone) 92.dp else 108.dp
    val recentReadingListState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(state.readingProgress, selectedReadingIds) {
        val existingIds = state.readingProgress.map { it.uniqueId }.toSet()
        val filteredIds = selectedReadingIds.filter { it in existingIds }
        if (filteredIds != selectedReadingIds) {
            selectedReadingIds = filteredIds
        }
    }

    LaunchedEffect(visibleReadingProgress.map { it.uniqueId to it.currentPage }) {
        if (visibleReadingProgress.isNotEmpty()) {
            recentReadingListState.scrollToItem(0)
        }
    }

    LaunchedEffect(pendingDeleteProgresses) {
        if (pendingDeleteProgresses.isNotEmpty()) {
            val pendingIds = pendingDeleteProgresses.map { it.uniqueId }
            undoProgress.snapTo(1f)
            undoProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 4200)
            )
            if (pendingDeleteProgresses.map { it.uniqueId } == pendingIds) {
                pendingDeleteProgresses = emptyList()
                onDeleteReading(pendingIds)
            }
        } else {
            undoProgress.snapTo(0f)
        }
    }
    BackHandler(enabled = isSelectionMode) {
        selectedReadingIds = emptyList()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // 移除外层的横向 padding，我们将它们下放到内部元素
                .padding(top = contentPadding, bottom = contentPadding)
        ) {
        // 1. Header (Greeting + Notification)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = contentPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "$greeting, ${state.userName}",
                    style = if (isPhone) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "\u4eca\u5929\u662f $currentDate",
                    style = if (isPhone) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
            
            // Notification Icon
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                onClick = { /* mock */ },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = "Notifications",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(if (isPhone) 12.dp else 24.dp)) // 从 20.dp/32.dp 收紧间距

            // 2. Hero Card (Up Next)
            Text(
                text = "\u4eca\u65e5\u4f1a\u8bae", // "今日会议"
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = contentPadding)
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
                    contentPadding = PaddingValues(horizontal = contentPadding), // 为卡片滑动加上 Padding
                    pageSpacing = if (isPhone) 12.dp else 16.dp,
                    modifier = Modifier.fillMaxWidth().height(heroCardHeight) 
                ) { virtualPage ->
                    // 闂佸搫鍟版繛鈧俊鍓у劋濞碱亪顢欓懞銉ュ晩闂佸搫鍟抽崺鏍ｈ娴滄悂宕熼銏㈠帓闂佹椿浜為崰搴ㄦ偪閸曨剙顕辨慨妯诲墯閸炲绱掓笟鍨仼缂?
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
                        modifier = Modifier.fillMaxWidth().padding(horizontal = contentPadding),
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
                    .height(if (isPhone) 100.dp else 120.dp) // 压缩空状态高度 120->100
                    .padding(horizontal = contentPadding)
                    .clip(RoundedCornerShape(16.dp)) // 圆角降为 16.dp
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
                         modifier = Modifier.size(36.dp) // 图标从 48 -> 36
                     )
                     Spacer(modifier = Modifier.height(8.dp)) // 间距 12 -> 8
                     Text(
                         text = "\u4eca\u65e5\u6682\u65e0\u4f1a\u8bae\u5b89\u6392",
                         style = MaterialTheme.typography.titleMedium,
                         color = MaterialTheme.colorScheme.onSurface,
                         fontWeight = FontWeight.Medium
                     )
                 }
             }
        }

        Spacer(modifier = Modifier.height(if (isPhone) 16.dp else 24.dp))

        // ── 中部功能区：左侧快捷功能 + 右侧最近阅读 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = contentPadding),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // 左侧列：快捷功能标题 + 卡片
            Column(modifier = Modifier.weight(0.4f)) {
                Text(
                    text = "快捷功能",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionPrimaryButton(
                        icon = Icons.Default.HowToVote,
                        title = "投票",
                        onClick = onVoteClick
                    )
                    QuickActionPrimaryButton(
                        icon = Icons.Default.Refresh,
                        title = "抽签",
                        onClick = onLotteryClick
                    )
                }
            }

            // 右侧列：最近阅读标题 + 卡片
            Column(modifier = Modifier.weight(0.6f)) {
                Text(
                    text = if (isSelectionMode) "已选择 ${selectedReadingIds.size} 项" else "最近阅读",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        // 选择模式下的操作栏
                        if (isSelectionMode) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (selectedReadingIds.size == 1) {
                                        selectedProgress.firstOrNull()?.fileName ?: "可批量管理最近阅读"
                                    } else {
                                        "可批量移除，不会删除 PDF"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1
                                )
                                FilledTonalButton(
                                    onClick = {
                                        val newPendingItems = state.readingProgress
                                            .filter { it.uniqueId in selectedReadingIds }
                                        if (newPendingItems.isNotEmpty()) {
                                            pendingDeleteProgresses = (pendingDeleteProgresses + newPendingItems)
                                                .distinctBy { it.uniqueId }
                                            selectedReadingIds = emptyList()
                                        }
                                    },
                                    enabled = selectedReadingIds.isNotEmpty(),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("删除")
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                TextButton(onClick = { selectedReadingIds = emptyList() }) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (visibleReadingProgress.isNotEmpty()) {
                            LazyRow(
                                state = recentReadingListState,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(
                                    items = visibleReadingProgress,
                                    key = { it.uniqueId }
                                ) { progress ->
                                    Box(modifier = Modifier.animateItemPlacement()) {
                                        com.example.paperlessmeeting.ui.components.RecentReadingCard(
                                            progress = progress,
                                            isSelectionMode = isSelectionMode,
                                            isSelected = progress.uniqueId in selectedReadingIds,
                                            isDeleting = false,
                                            onClick = {
                                                if (isSelectionMode) {
                                                    selectedReadingIds = if (progress.uniqueId in selectedReadingIds) {
                                                        selectedReadingIds.filterNot { it == progress.uniqueId }
                                                    } else {
                                                        selectedReadingIds + progress.uniqueId
                                                    }
                                                } else {
                                                    onReadingClick(progress.uniqueId, progress.fileName, progress.currentPage)
                                                }
                                            },
                                            onLongClick = {
                                                if (progress.uniqueId !in pendingDeleteIds) {
                                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    selectedReadingIds = if (progress.uniqueId in selectedReadingIds) {
                                                        selectedReadingIds
                                                    } else {
                                                        selectedReadingIds + progress.uniqueId
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(112.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.MenuBook,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "暂无阅读记录",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(if (isPhone) 128.dp else 148.dp))
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = pendingDeleteProgresses.isNotEmpty(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = undoBarBottomPadding),
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 2 },
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it / 2 }
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth(if (isPhone) 0.9f else 0.58f)
                    .widthIn(max = if (isPhone) 380.dp else 440.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 14.dp, end = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = pendingDeleteProgresses.size.toString(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (pendingDeleteProgresses.size == 1) {
                                    "已移除《${pendingDeleteProgresses.first().fileName}》"
                                } else {
                                    "已移除 ${pendingDeleteProgresses.size} 项最近阅读"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Text(
                                text = "仅删除阅读记录，不影响 PDF 文件 · 4 秒内可撤销",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }

                        FilledTonalButton(
                            onClick = { pendingDeleteProgresses = emptyList() },
                            shape = RoundedCornerShape(18.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text("撤销")
                        }
                    }

                    LinearProgressIndicator(
                        progress = { undoProgress.value },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    )
                }
            }
        }
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
    val context = LocalContext.current
    val contentPadding = if (isPhone) 16.dp else 24.dp
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

@Composable
fun QuickActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
            onClick = onClick,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(10.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun QuickActionPrimaryButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f)),
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(6.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
