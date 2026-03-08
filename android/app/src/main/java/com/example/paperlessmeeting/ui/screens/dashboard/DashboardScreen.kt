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
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
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
        now.format(DateTimeFormatter.ofPattern("yy\u5e74M\u6708d\u65e5 EEEE", Locale.CHINA))
    }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val isPhone = screenWidthDp < 600
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

    LaunchedEffect(state.readingProgress, selectedReadingIds) {
        val existingIds = state.readingProgress.map { it.uniqueId }.toSet()
        val filteredIds = selectedReadingIds.filter { it in existingIds }
        if (filteredIds != selectedReadingIds) {
            selectedReadingIds = filteredIds
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
            text = "\u4eca\u5929\u662f $currentDate",
            style = if (isPhone) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(if (isPhone) 20.dp else 32.dp))

        // 2. Hero Card (Up Next)
        Text(
            text = "\u4eca\u65e5\u4f1a\u8bae\u5b89\u6392",
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
                         text = "\u4eca\u65e5\u6682\u65e0\u4f1a\u8bae\u5b89\u6392",
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
            text = "\u5feb\u6377\u529f\u80fd",
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
                    label = "\u6295\u7968",
                    onClick = onVoteClick
                )

                QuickActionButton(
                    icon = Icons.Default.Refresh,
                    label = "\u62bd\u7b7e",
                    onClick = onLotteryClick
                )

                /* 闂傚倸鍊搁崐鎼佸磹閹间礁纾瑰瀣捣閻棗銆掑锝呬壕濡ょ姷鍋為悧鐘汇€侀弴銏犵厬闁兼亽鍎抽埥澶愭懚閺嶎厽鐓曟繛鎴濆船楠炴﹢鏌ㄥ☉娆戞噰婵﹥妞介幊锟犲Χ閸涱喚浜梻浣侯焾椤戝懘鏁冮妶澶娢ラ柟鐑樻尵缁♀偓濠殿喗锕╅崢楣冨储闁秵鍊甸柛蹇擃槸娴滈箖姊洪柅鐐茶嫰婢у鈧娲橀崹鍧楀箖濞嗘挸浼犻柛鏇ㄥ幖楠炲牓姊绘担鍛婃儓婵炲眰鍨藉畷鐟懊洪鍛簵濠电偛妫欓幐濠氬煕閹寸偑浜滈柟鎯у船婵″潡鏌ｉ敐澶夋喚闁哄矉缍€缁犳稒绻濋崒姘ｆ嫟闂備線娼уú銈団偓姘卞娣囧﹪鎮滈懞銉︽珖闂侀€炲苯澧撮柟顔斤耿楠炴﹢顢欓悾灞藉箰闁诲骸鍘滈崑鎾绘煃瑜滈崜鐔风暦娴兼潙鍐€鐟滃繘寮抽敂鐐枑闁绘鐗嗘穱顖炴煛娴ｇ鏆ｉ柡灞诲妼閳规垿宕卞Ο鐑橆仱婵＄偑鍊х徊楣冩偂閿熺姴钃熼柨婵嗘閸庣喖鏌曢崼婵嗩劉缂傚秴鐗嗛埞鎴︽倷鐠鸿櫣姣㈤梺鍝ュУ閸旀鍒掔€ｎ亶鍚嬪璺猴躬閸炲爼姊洪崫鍕窛濠殿喖顕划濠囨晝閸屾稈鎷洪悷婊呭鐢帗绂嶆导瀛樼厱婵☆垰鐏濇禍瑙勭箾閸℃劕鐏查柟顔界懇閹粌螣閻撳骸绠ラ梻鍌氬€风欢锟犲矗韫囨洜涓嶉柟杈惧瀹撲線鏌″搴″箺闁?
                QuickActionButton(
                    icon = Icons.Default.Edit,
                    label = "闂傚倸鍊搁崐鎼佸磹閹间礁纾归柣鎴ｅГ閸ゅ嫰鏌涢锝嗙缂佺姷濞€閺岀喖宕滆鐢盯鏌涙繝鍛厫闁逛究鍔岃灒闁圭娴烽妴鎰磽娴ｅ搫校濠㈢懓妫涘Σ鎰板箳閺傚搫浜鹃柨婵嗛楠炴鐥鐐靛煟闁?,
                    onClick = onCheckInClick
                )
                */
            }
        }

        Spacer(modifier = Modifier.height(if (isPhone) 20.dp else 32.dp))

        // 3. Recent Reading (Using reading progress)
        if (isSelectionMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "已选择 ${selectedReadingIds.size} 项",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (selectedReadingIds.size == 1) {
                            selectedProgress.firstOrNull()?.fileName ?: "可批量管理最近阅读"
                        } else {
                            "可批量移除最近阅读记录，不会删除 PDF 文件"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

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
                    enabled = selectedReadingIds.isNotEmpty()
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("删除")
                }

                Spacer(modifier = Modifier.width(8.dp))

                TextButton(onClick = { selectedReadingIds = emptyList() }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("取消")
                }
            }
        } else {
            Text(
                text = "最近阅读",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        
        if (visibleReadingProgress.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(
                    items = visibleReadingProgress,
                    key = { it.uniqueId }
                ) { progress ->
                    Box(
                        modifier = Modifier.animateItemPlacement()
                    ) {
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
            // 缂傚倸鍊搁崐椋庣矆娓氣偓閹本鎷呯化鏇熺€洪梺鎸庣箓濡瑩宕ｈ箛娑欑厵闂傚倸顕ˇ锔剧磼閻樺啿鐏ラ棁澶愭煥濠靛棙澶勯柛銈傚亾缂傚倷鐒﹁ぐ鍐耿鏉堚晜顫曢柟鐑橆殔閻?
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
                        text = "\u6682\u65e0\u9605\u8bfb\u8bb0\u5f55",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
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



