package com.example.paperlessmeeting.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
fun DashboardScreen() {
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
        
        HeroMeetingCard()

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
            items(5) {
                RecentFileCard(index = it)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun MeetingStatsCard() {
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
fun HeroMeetingCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = "https://images.unsplash.com/photo-1542744173-8e7e53415bb0?q=80&w=2070&auto=format&fit=crop",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.2f), Color.Black.copy(alpha = 0.8f))
                        )
                    )
            )
            
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(24.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "进行中",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "2025年度战略规划复盘会",
                    style = MaterialTheme.typography.titleLarge, // Slightly smaller than headline for fit
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "14:00 - 16:30 | 101 核心会议室",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { /* Check-in or Enter */ },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(
                        Icons.Default.PlayArrow, 
                        contentDescription = null, 
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("立即入会", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun RecentFileCard(index: Int) {
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
                 Text(
                     text = "PDF", 
                     modifier = Modifier.align(Alignment.Center),
                     color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                     fontWeight = FontWeight.Bold
                 )
            }
            // Metadata
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "财务报告_Q1_v${index+1}.pdf",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "3.2 MB • 昨天",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}
