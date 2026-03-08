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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.delay
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
                onDeleteReading = viewModel::deleteReadingProgress,
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
    onDeleteReading: (String) -> Unit,
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
    var deleteTargetId by rememberSaveable(state.readingProgress) { mutableStateOf<String?>(null) }
    var confirmDeleteId by remember { mutableStateOf<String?>(null) }
    var confirmDeleteName by remember { mutableStateOf<String?>(null) }

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
                    // 闁哄啰濞€濡剧儤娼鑺ュ啊闁哄啳鍩栧Σ褏浜搁崟顐㈢厒闁活亞鍠庨悿鍕濮樻剚鍞寸紒渚垮灩缁?
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

                /* 闂傚倸鍊搁崐鎼佸磹妞嬪海鐭嗗〒姘ｅ亾妤犵偞鐗犻、鏇㈠煑閼恒儳鈽夐摶鏍煕濞戝崬骞橀柨娑欑懇濮婃椽鎳￠妶鍛亪闂佺顑呴敃銈夊Υ閹烘挾绡€婵﹩鍘鹃崢閬嶆倵閸忓浜鹃梺閫炲苯澧寸€规洘鍨块幃娆撳传閸曨厼骞堥梻浣告惈濞层垽宕瑰ú顏呭亗婵炲棙鎸婚埛鎴炪亜閹惧崬濡块柣锝変憾閺岋綀绠涙繝鍌氣拤闂侀潧娲ょ€氱増淇婇悜鑺ユ櫇闁逞屽墴閹﹢骞橀鐣屽幐閻庡厜鍋撻柍褜鍓熷畷浼村冀瑜忛弳锔炬喐閻楀牆淇柡浣稿暣閺屻劌鈹戦崱妯烘濡炪倧绲鹃悡锟犲蓟閿濆棙鍎熼柕鍫濆缂嶅牆鈹戦悙璺虹毢闁哥姵鍔楃划瀣吋婢跺﹪鍞堕梺鍝勬川婵绮婇敃鍌涒拺鐟滅増甯掓禍浼存煕濡灝浜规繛鍡愬灲閹瑩鎮滃Ο鐓庡箥闂傚倷绶￠崣蹇曠不閹达妇宓侀柡宥庡幗閻?
                QuickActionButton(
                    icon = Icons.Default.Edit,
                    label = "闂傚倸鍊搁崐鎼佸磹閻戣姤鍤勯柛顐ｆ礀缁犵娀鏌熼崜褏甯涢柛濠呭煐閹便劌螣閹稿海銆愮紓浣哄У婢瑰棛妲愰幒鏂哄亾閿濆骸骞楃痪顓炵埣閺?,
                    onClick = onCheckInClick
                )
                */
            }
        }

        Spacer(modifier = Modifier.height(if (isPhone) 20.dp else 32.dp))

        // 3. Recent Reading (Using reading progress)
        Text(
            text = "\u6700\u8fd1\u9605\u8bfb",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        if (state.readingProgress.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(
                    items = state.readingProgress,
                    key = { it.uniqueId }
                ) { progress ->
                    Box(
                        modifier = Modifier.animateItemPlacement()
                    ) {
                        com.example.paperlessmeeting.ui.components.RecentReadingCard(
                            progress = progress,
                            showDeleteAction = deleteTargetId == progress.uniqueId,
                            isDeleting = false,
                            onClick = {
                                if (deleteTargetId == progress.uniqueId) {
                                    deleteTargetId = null
                                } else {
                                    onReadingClick(progress.uniqueId, progress.fileName, progress.currentPage)
                                }
                            },
                            onLongClick = {
                                deleteTargetId = progress.uniqueId
                            },
                            onDeleteClick = {
                                confirmDeleteId = progress.uniqueId
                                confirmDeleteName = progress.fileName
                            }
                        )
                    }
                }
            }
        } else {
            // 缂傚倸鍊风粈渚€鎯屾担绯曟瀺闁挎繂妫欓崣蹇涙煙闂傚顦︾紒鐘冲灥闇夐柨婵嗘处閸も偓缂備焦褰冨﹢杈╂閹烘鐒?
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

        if (confirmDeleteId != null) {
            AlertDialog(
                onDismissRequest = {
                    confirmDeleteId = null
                    confirmDeleteName = null
                },
                modifier = Modifier.widthIn(max = 280.dp),
                title = {
                    Text("\u786e\u8ba4\u5220\u9664", style = MaterialTheme.typography.titleSmall)
                },
                text = {
                    Text(
                        "\u786e\u5b9a\u8981\u5220\u9664\u300c${confirmDeleteName ?: "\u8be5\u6587\u4ef6"}\u300d\u5417\uff1f",
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val pendingId = confirmDeleteId ?: return@TextButton
                            confirmDeleteId = null
                            confirmDeleteName = null
                            deleteTargetId = null
                            onDeleteReading(pendingId)
                        }
                    ) {
                        Text("\u5220\u9664")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            confirmDeleteId = null
                            confirmDeleteName = null
                        }
                    ) {
                        Text("\u53d6\u6d88")
                    }
                }
            )
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
