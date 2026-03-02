package com.example.paperlessmeeting.ui.screens.checkin

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.paperlessmeeting.domain.model.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInDashboardScreen(
    onBackClick: () -> Unit,
    viewModel: CheckInDashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // 全局消息监听
    LaunchedEffect(uiState.actionMessage) {
        uiState.actionMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // 使用透明 TopBar，让背景透出来
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                        )
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. 顶部背景装饰 (渐变大圆球)
            val gradientStartColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
            val gradientEndColor = MaterialTheme.colorScheme.background
            
            Canvas(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                drawCircle(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            gradientStartColor,
                            gradientEndColor
                        )
                    ),
                    center = Offset(size.width / 2, -100f),
                    radius = size.width * 0.8f
                )
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(scrollState)
                ) {
                    // 2. 头部：用户状态概览
                    HeaderSection(uiState)

                    Spacer(modifier = Modifier.height(24.dp))

                    // 3. 核心功能区 (悬浮卡片效果)
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .offset(y = (-20).dp) // 向上浮动，压住背景
                    ) {
                        // 签到卡片
                        ActionCard(
                            todayStatus = uiState.todayStatus,
                            onCheckIn = { viewModel.checkIn(it) },
                            onMakeup = { id, remark -> viewModel.makeupCheckIn(id, remark) }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // 4. 数据统计区
                        StatsSection(
                            stats = uiState.stats,
                            selectedRange = uiState.selectedRange,
                            onRangeChange = { viewModel.switchRange(it) }
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))

                        // 5. 活跃度热力图 (GitHub Style)
                        if (uiState.heatmap.isNotEmpty()) {
                            SectionTitle("会议活跃度", Icons.Default.DateRange)
                            HeatmapCard(heatmapData = uiState.heatmap)
                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        // 6. 图表与协作 (左右布局或上下布局)
                        SectionTitle("数据洞察", Icons.Default.PieChart)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            // 环形图
                            if (uiState.typeDistribution.isNotEmpty()) {
                                Box(modifier = Modifier.weight(1f)) {
                                    DonutChartCard(data = uiState.typeDistribution)
                                }
                            }
                            // 环保卡片
                            Box(modifier = Modifier.weight(1f)) {
                                EcoCard(readingCount = uiState.stats.readingCount)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // 7. 常用协作人
                        if (uiState.collaborators.isNotEmpty()) {
                            SectionTitle("最佳拍档", Icons.Outlined.Group)
                            CollaboratorsRow(collaborators = uiState.collaborators)
                        }
                        
                        Spacer(modifier = Modifier.height(40.dp))
                    }
                }
            }
        }
    }
}

// ================= 子组件设计 =================

@Composable
fun HeaderSection(uiState: CheckInDashboardUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Hello, 参会人",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "让每一次会议都井井有条",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun ActionCard(
    todayStatus: TodayStatusResponse?,
    onCheckIn: (Int) -> Unit,
    onMakeup: (Int, String) -> Unit
) {
    val unchecked = todayStatus?.todayMeetings?.filter { !it.checkedIn } ?: emptyList()
    val isAllDone = todayStatus != null && unchecked.isEmpty() && todayStatus.todayMeetings.isNotEmpty()
    val isNoMeeting = todayStatus == null || todayStatus.todayMeetings.isEmpty()

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧图标区
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isNoMeeting -> MaterialTheme.colorScheme.surfaceVariant
                            isAllDone -> Color(0xFFE8F5E9) // Green lighter
                            else -> MaterialTheme.colorScheme.primaryContainer
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        isNoMeeting -> Icons.Default.EventBusy
                        isAllDone -> Icons.Default.EmojiEvents
                        else -> Icons.Default.Alarm
                    },
                    contentDescription = null,
                    tint = when {
                        isNoMeeting -> MaterialTheme.colorScheme.onSurfaceVariant
                        isAllDone -> Color(0xFF2E7D32)
                        else -> MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 中间文字区
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        isNoMeeting -> "今日暂无会议"
                        isAllDone -> "今日会议已全勤"
                        else -> "待参加: ${unchecked.first().meetingTitle}"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = when {
                        isNoMeeting -> "好好休息一下吧"
                        isAllDone -> "表现完美，继续保持！"
                        else -> "开始时间: ${unchecked.first().startTime.substring(11, 16)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 右侧按钮区
            if (!isNoMeeting && !isAllDone) {
                Button(
                    onClick = { onCheckIn(unchecked.first().meetingId) },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Text("签到")
                }
            } else if (isAllDone) {
                Icon(
                    Icons.Outlined.CheckCircle, 
                    contentDescription = "Done", 
                    tint = Color(0xFF2E7D32)
                )
            }
        }
    }
}

@Composable
fun StatsSection(
    stats: DashboardStats,
    selectedRange: String,
    onRangeChange: (String) -> Unit
) {
    Column {
        // 时间切换器 (胶囊状)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("week" to "本周", "month" to "本月", "year" to "本年").forEach { (key, label) ->
                val isSelected = selectedRange == key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { onRangeChange(key) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2x2 网格卡片
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // 使用自定义 AnimatedStatCard
            AnimatedStatCard(
                modifier = Modifier.weight(1f),
                label = "参会总数",
                value = stats.meetingCount,
                unit = "场",
                icon = Icons.Default.MeetingRoom,
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
            AnimatedStatCard(
                modifier = Modifier.weight(1f),
                label = "累计时长",
                value = stats.totalDurationMinutes / 60, // 换算成小时
                unit = "小时",
                icon = Icons.Default.AccessTime,
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AnimatedStatCard(
                modifier = Modifier.weight(1f),
                label = "已签到",
                value = stats.checkinCount,
                unit = "次",
                icon = Icons.Default.FactCheck,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
            AnimatedStatCard(
                modifier = Modifier.weight(1f),
                label = "阅读文件",
                value = stats.readingCount,
                unit = "份",
                icon = Icons.Default.Description,
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        }
    }
}

@Composable
fun AnimatedStatCard(
    modifier: Modifier,
    label: String,
    value: Int,
    unit: String,
    icon: ImageVector,
    containerColor: Color
) {
    // 数字滚动动画
    val animatedValue by animateIntAsState(
        targetValue = value,
        animationSpec = tween(durationMillis = 1000)
    )

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$animatedValue",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

// 仿 GitHub 热力图
@Composable
fun HeatmapCard(heatmapData: Map<String, Int>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 简单处理：显示最近 12 周的数据 (这里只是模拟布局，真实情况需要精确的日期计算)
            // 假设 heatmapData key 是 "yyyy-MM-dd"
            
            // 构建一个 7行 x 12列 的网格
            val rows = 7
            val cols = 15 
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                repeat(cols) { col ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(rows) { row ->
                            // 模拟数据获取 (真实逻辑需要根据 col/row 推算日期)
                            // 这里仅做随机演示色块，你需要根据 heatmapData[date] 来决定颜色深度
                            // 实际开发建议封装一个 CalendarLogic 来计算 (col, row) -> date
                            
                            val intensity = (Math.random() * 5).toInt() // 0-4
                            val color = when(intensity) {
                                0 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                1 -> Color(0xFFC8E6C9)
                                2 -> Color(0xFF81C784)
                                3 -> Color(0xFF4CAF50)
                                else -> Color(0xFF2E7D32)
                            }
                            
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(color)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text("Less", style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.width(4.dp))
                Box(modifier = Modifier.size(10.dp).background(MaterialTheme.colorScheme.surfaceVariant))
                Spacer(modifier = Modifier.width(2.dp))
                Box(modifier = Modifier.size(10.dp).background(Color(0xFF4CAF50)))
                Spacer(modifier = Modifier.width(4.dp))
                Text("More", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun DonutChartCard(data: List<TypeDistributionItem>) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.aspectRatio(1f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val total = data.sumOf { it.count }.toFloat()
            // 动画状态
            var animationPlayed by remember { mutableStateOf(false) }
            val animatedProgress by animateFloatAsState(
                targetValue = if (animationPlayed) 1f else 0f,
                animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
            )

            LaunchedEffect(Unit) { animationPlayed = true }

            Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    var startAngle = -90f
                    val strokeWidth = 20.dp.toPx()
                    
                    if (data.isEmpty()) {
                        drawCircle(color = Color.LightGray, style = Stroke(width = strokeWidth))
                    } else {
                        val colors = listOf(Color(0xFF5C6BC0), Color(0xFF26A69A), Color(0xFFFFA726), Color(0xFFEC407A))
                        
                        data.forEachIndexed { index, item ->
                            val sweepAngle = (item.count / total) * 360f * animatedProgress
                            drawArc(
                                color = colors[index % colors.size],
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                            startAngle += sweepAngle
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text((total * animatedProgress).toInt().toString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("类型分布", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun EcoCard(readingCount: Int) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)), // Light Green
        modifier = Modifier.aspectRatio(1f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Forest, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            val savedTrees = String.format("%.2f", readingCount * 0.005) // 假设值
            Text(savedTrees, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
            Text("拯救树木", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
        }
    }
}

@Composable
fun CollaboratorsRow(collaborators: List<Collaborator>) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(collaborators) { person ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        person.name.take(1),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(person.name, style = MaterialTheme.typography.labelSmall)
                Text("${person.coMeetings}场", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
fun SectionTitle(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 12.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}