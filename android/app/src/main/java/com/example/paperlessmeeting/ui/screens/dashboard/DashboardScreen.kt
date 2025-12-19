package com.example.paperlessmeeting.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

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
            DashboardContent(state)
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DashboardContent(state: DashboardUiState.Success) {
    // Debug log
    android.util.Log.d("DashboardDebug", "Active Meetings Count: ${state.activeMeetings.size}")
    state.activeMeetings.forEach { 
        android.util.Log.d("DashboardDebug", "Meeting: ${it.title}, TypeName: ${it.meetingTypeName}")
    }

    val currentHour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greeting = when (currentHour) {
        in 5..11 -> "上午好"
        in 12..18 -> "下午好"
        else -> "晚上好"
    }
    
    val currentDate = remember { 
        val now = java.time.LocalDate.now()
        now.format(DateTimeFormatter.ofPattern("yy年MM月dd日 EEEE", Locale.CHINA))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        // 1. Header
        Text(
            text = "$greeting, 张总",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "今天是 $currentDate",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 2. Hero Card (Up Next)
        Text(
            text = "当前会议",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        if (state.activeMeetings.isNotEmpty()) {
            val pagerState = rememberPagerState(
                initialPage = state.initialPageIndex,
                pageCount = { state.activeMeetings.size }
            )
            
            androidx.compose.runtime.LaunchedEffect(state.initialPageIndex) {
                if (state.activeMeetings.isNotEmpty()) {
                    pagerState.scrollToPage(state.initialPageIndex)
                }
            }
            
            Column {
                HorizontalPager(
                    state = pagerState,
                    pageSpacing = 16.dp,
                    // If we want peek, we need to adjust padding. For now, full width cards.
                    modifier = Modifier.fillMaxWidth().height(200.dp) // Implicit height or let it wrap? MeetingCard has height?
                ) { page ->
                    com.example.paperlessmeeting.ui.components.MeetingCard(
                        meeting = state.activeMeetings[page],
                        onClick = { /* TODO: Navigate */ }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Indicators
                if (state.activeMeetings.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(state.activeMeetings.size) { iteration ->
                            val color = if (pagerState.currentPage == iteration) 
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
                    .height(120.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
             ) {
                 Text("暂无会议安排", color = MaterialTheme.colorScheme.onSurfaceVariant)
             }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 3. Meeting Stats / Heatmap
        Text(
            text = "参会数据概览",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        MeetingStatsCard()

        Spacer(modifier = Modifier.height(32.dp))

        // 4. Recent Files
        Text(
            text = "最近阅读文件",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(state.recentFiles.size) { index ->
                RecentFileCard(state.recentFiles[index])
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun MeetingStatsCard() {
    // ... (Stats card implementation remains same for now)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "本周参会",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                    Text(
                        text = "12 小时",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Mini indicator
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "+15% 较上周",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Heatmap / Week View Visualization
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val weekDays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
                val intensities = listOf(0.8f, 0.4f, 1.0f, 0.2f, 0.6f, 0.0f, 0.0f) // 0.0 - 1.0
                
                weekDays.zip(intensities).forEach { (day, intensity) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Bar/Dot
                        val color = if (intensity > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f + (intensity * 0.8f)) 
                                    else MaterialTheme.colorScheme.surfaceVariant
                                    
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color),
                            contentAlignment = Alignment.Center
                        ) {
                             if (intensity > 0.5f) {
                                 Text(
                                     text = "${(intensity * 4).toInt()}h",
                                     style = MaterialTheme.typography.labelSmall,
                                     color = if (intensity > 0.7) Color.White else Color.Black,
                                     fontSize = 10.sp
                                 )
                             }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = day.take(1), // Mon -> M
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }
            }
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
