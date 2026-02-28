package com.example.paperlessmeeting.ui.screens.checkin

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.paperlessmeeting.domain.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInDashboardScreen(
    onBackClick: () -> Unit,
    viewModel: CheckInDashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.actionMessage) {
        uiState.actionMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("个人会议数据看板", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = uiState.error ?: "加载失败", color = MaterialTheme.colorScheme.error)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                // 1. 时间维度切换器
                item {
                    TimeRangeSelector(
                        selectedRange = uiState.selectedRange,
                        onRangeSelected = { viewModel.switchRange(it) }
                    )
                }

                // 2. 核心操作区 & 环保卡片 (一行两个方块)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        HeroCheckInCard(
                            todayStatus = uiState.todayStatus,
                            onCheckIn = { meetingId -> viewModel.checkIn(meetingId) },
                            onMakeup = { meetingId, remark -> viewModel.makeupCheckIn(meetingId, remark) },
                            modifier = Modifier.weight(1f).aspectRatio(1f) // 使其成为正方形
                        )
                        EcoImpactCard(
                            readingCount = uiState.stats.readingCount,
                            modifier = Modifier.weight(1f).aspectRatio(1f)
                        )
                    }
                }

                // 3. 数据概览网格
                item {
                    StatsGrid(stats = uiState.stats)
                }

                // 4. 可视化图表 - 类型分布
                if (uiState.typeDistribution.isNotEmpty()) {
                    item {
                        ChartSectionTitle("参会类型分布")
                        TypeDistributionChart(data = uiState.typeDistribution)
                    }
                }

                // 5. 协作关系
                if (uiState.collaborators.isNotEmpty()) {
                    item {
                        ChartSectionTitle("经常一起开会的人")
                        CollaboratorsCard(collaborators = uiState.collaborators)
                    }
                }

                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
fun HeroCheckInCard(
    modifier: Modifier = Modifier,
    todayStatus: TodayStatusResponse?,
    onCheckIn: (Int) -> Unit,
    onMakeup: (Int, String) -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (todayStatus == null || todayStatus.todayMeetings.isEmpty()) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("今日无会", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
            } else {
                val unchecked = todayStatus.todayMeetings.filter { !it.checkedIn }
                val checked = todayStatus.todayMeetings.filter { it.checkedIn }

                when {
                    unchecked.isNotEmpty() -> {
                        // 有未打卡的会议
                        val nextMeeting = unchecked.first()
                        Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(nextMeeting.meetingTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(nextMeeting.startTime.substring(11, 16), style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.weight(1f))
                        
                        Button(
                            onClick = { onCheckIn(nextMeeting.meetingId) },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("签到", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    else -> {
                        // 全部打卡完毕
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("全签到完", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.weight(1f))
                        Text("共签到 ${checked.size} 次", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun TimeRangeSelector(selectedRange: String, onRangeSelected: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        val ranges = listOf("week" to "本周", "month" to "本月", "year" to "本年")
        ranges.forEach { (key, label) ->
            FilterChip(
                selected = selectedRange == key,
                onClick = { onRangeSelected(key) },
                label = { Text(label) },
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
fun StatsGrid(stats: DashboardStats) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard(modifier = Modifier.weight(1f), title = "参会总数", value = stats.meetingCount.toString(), subtitle = "场", color = Color(0xFFE3F2FD), textColor = Color(0xFF1565C0))
            StatCard(modifier = Modifier.weight(1f), title = "签到数", value = stats.checkinCount.toString(), subtitle = "次", color = Color(0xFFF3E5F5), textColor = Color(0xFF6A1B9A))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard(modifier = Modifier.weight(1f), title = "参与类型", value = stats.typeCount.toString(), subtitle = "种", color = Color(0xFFFFF3E0), textColor = Color(0xFFEF6C00))
            StatCard(modifier = Modifier.weight(1f), title = "阅读文件", value = stats.readingCount.toString(), subtitle = "份", color = Color(0xFFE0F7FA), textColor = Color(0xFF00838F))
        }
    }
}

@Composable
fun StatCard(modifier: Modifier = Modifier, title: String, value: String, subtitle: String, color: Color, textColor: Color) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = textColor.copy(alpha = 0.8f), fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = textColor)
                Spacer(modifier = Modifier.width(4.dp))
                Text(subtitle, style = MaterialTheme.typography.titleSmall, color = textColor.copy(alpha = 0.8f), modifier = Modifier.padding(bottom = 6.dp))
            }
        }
    }
}

@Composable
fun ChartSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
    )
}

@Composable
fun TypeDistributionChart(data: List<TypeDistributionItem>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val total = data.sumOf { it.count }.toFloat()
            val colors = listOf(Color(0xFF5C6BC0), Color(0xFF26A69A), Color(0xFFFFA726), Color(0xFFAB47BC), Color(0xFFEC407A))
            
            // 简单的环形图
            Box(modifier = Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    var startAngle = -90f
                    data.forEachIndexed { index, item ->
                        val sweepAngle = (item.count / total) * 360f
                        val color = colors[index % colors.size]
                        drawArc(
                            color = color,
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            style = Stroke(width = 30f, cap = StrokeCap.Butt)
                        )
                        startAngle += sweepAngle
                    }
                }
                Text("${total.toInt()}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.width(24.dp))
            
            // 图例
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                data.take(5).forEachIndexed { index, item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(colors[index % colors.size]))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(item.typeName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text("${item.count}次", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun CollaboratorsCard(collaborators: List<Collaborator>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            collaborators.forEachIndexed { index, collaborator ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${index + 1}", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(collaborator.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Text("共同参加 ${collaborator.coMeetings} 场", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (index < collaborators.size - 1) {
                    HorizontalDivider(modifier = Modifier.padding(start = 44.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
fun EcoImpactCard(modifier: Modifier = Modifier, readingCount: Int) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🌱 无纸化", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            val savedPaper = readingCount * 5 // 假设每份文件看5章/页
            val savedTrees = String.format("%.3f", savedPaper * 0.0001)
            Text(
                "$savedTrees",
                color = Color(0xFF1B5E20),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text("棵拯救的树", style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32))
        }
    }
}
