package com.example.paperlessmeeting.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.example.paperlessmeeting.domain.model.Vote
import com.example.paperlessmeeting.domain.model.VoteOption
import com.example.paperlessmeeting.domain.model.VoteResult
import kotlinx.coroutines.delay

import com.example.paperlessmeeting.ui.theme.PrimaryBlue
import com.example.paperlessmeeting.ui.theme.PrimaryBlueLight
import com.example.paperlessmeeting.ui.theme.BackgroundLayer
import com.example.paperlessmeeting.ui.theme.SurfaceWhite
import com.example.paperlessmeeting.ui.theme.TextPrimary
import com.example.paperlessmeeting.ui.theme.TextSecondary
import com.example.paperlessmeeting.ui.theme.CardBackground

// 升级配色系统
private val SuccessGreen = Color(0xFF4CAF50)
private val WarningOrange = Color(0xFFFF9800)

/**
 * 投票 BottomSheet 组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoteBottomSheet(
    vote: Vote,
    hasVoted: Boolean,
    result: VoteResult?,
    onSubmit: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
    onFetchResult: (Int) -> Unit = {},
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    // Observe vote status changes to fetch result when closed
    LaunchedEffect(vote.status, vote.id) {
        if (vote.status == "closed" && result == null) {
            onFetchResult(vote.id)
        }
    }
    // 倒计时
    // 使用单一总时长来管理（等待时间 + 投票时间），避免时间叠加问题
    // 添加 vote.id 和 vote.status 作为 key，确保切换投票或状态变更时重置倒计时
    // 修复同步问题：优先使用 started_at 计算绝对剩余时间
    val calculatedTotalSeconds = remember(vote.id, vote.started_at, vote.status) {
        try {
            if (vote.started_at != null) {
                // 后端通常返回 "yyyy-MM-dd HH:mm:ss" 或 ISO 格式
                val cleanTime = vote.started_at.replace(" ", "T")
                val startTime = java.time.LocalDateTime.parse(cleanTime)
                val totalDuration = (vote.duration_seconds) + (vote.wait_seconds ?: 0)
                val endTime = startTime.plusSeconds(totalDuration.toLong())
                val now = java.time.LocalDateTime.now()
                val diff = java.time.temporal.ChronoUnit.SECONDS.between(now, endTime).toInt()
                // 如果已经过时，diff可能为负，UI会显示已结束
                maxOf(0, diff)
            } else {
                vote.remaining_seconds ?: ((vote.duration_seconds) + (vote.wait_seconds ?: 0))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // 解析失败降级处理
            vote.remaining_seconds ?: ((vote.duration_seconds) + (vote.wait_seconds ?: 0))
        }
    }

    var totalSeconds by remember(vote.id, vote.status) { 
        mutableIntStateOf(calculatedTotalSeconds) 
    }
    val durationSeconds = vote.duration_seconds

    val isWaiting = vote.status == "active" && totalSeconds > durationSeconds
    val waitingSeconds = if (isWaiting) totalSeconds - durationSeconds else 0
    val remainingSeconds = if (isWaiting) durationSeconds else totalSeconds
    
    val isActive = vote.status == "active" && totalSeconds > 0

    // 选中的选项 - 切换投票时重置
    var selectedOptions by remember(vote.id) { mutableStateOf<Set<Int>>(emptySet()) }

    // 倒计时逻辑
    LaunchedEffect(vote.status) {
        if (vote.status == "active") {
            while (totalSeconds > 0) {
                delay(1000)
                totalSeconds--
            }
            onFetchResult(vote.id)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BackgroundLayer,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // 顶部提示文案
            Text(
                text = buildAnnotatedString {
                    append("可在 ")
                    withStyle(style = SpanStyle(
                        color = PrimaryBlue,
                        fontWeight = FontWeight.Bold
                    )) {
                        append("首页-投票")
                    }
                    append(" 中查看")
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                textAlign = TextAlign.Center
            )

            // Header - 使用卡片背景提升视觉层次
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 图标背景
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                        colors = if (isWaiting) listOf(WarningOrange, Color(0xFFFF6F00)) 
                                                 else listOf(PrimaryBlue, PrimaryBlueLight)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (result != null) Icons.Default.Poll else Icons.Default.HowToVote,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = vote.title,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            if (!isWaiting) {
                                vote.description?.let { desc ->
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = desc,
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    
                    // 倒计时或状态
                    if (vote.status == "active") {
                        // 投票进行中 (含准备阶段)
                        Column {
                            if (isWaiting) {
                                WaitingBadge(waitingSeconds)
                            } else {
                                CountdownBadge(remainingSeconds)
                            }

                            if (hasVoted && !isWaiting) {
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "您已投票",
                                        color = SuccessGreen,
                                        fontWeight = FontWeight.Medium,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    } else {
                        StatusBadge("已结束", Color.Gray)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            if (result != null) {
                // ===== 结果视图 =====
                VoteResultView(result)
            } else if (hasVoted && !isWaiting) {
                // 已投票等待结果
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("您的投票已提交", style = MaterialTheme.typography.titleMedium)
                        Text("等待投票结束后查看结果", color = Color.Gray)
                    }
                }
            } else {
                // ===== 投票视图 =====
                // 如果正在等待，显示遮罩或禁用状态
                Box(contentAlignment = Alignment.Center) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.heightIn(max = 400.dp).then(if(isWaiting) Modifier.graphicsLayer { alpha = 0.5f } else Modifier)
                    ) {
                        items(vote.options) { option ->
                            VoteOptionCard(
                                option = option,
                                isSelected = option.id in selectedOptions,
                                enabled = isActive && !isWaiting, // 准备期间禁用
                                onClick = {
                                    selectedOptions = if (vote.is_multiple) {
                                        if (option.id in selectedOptions) {
                                            selectedOptions - option.id
                                        } else if (selectedOptions.size < vote.max_selections) {
                                            selectedOptions + option.id
                                        } else {
                                            selectedOptions
                                        }
                                    } else {
                                        setOf(option.id)
                                    }
                                }
                            )
                        }
                    }
                    
                    if (isWaiting) {
                         Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        ) {
                            Icon(Icons.Default.Timer, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("请稍候，投票即将开始", style = MaterialTheme.typography.titleMedium, color = WarningOrange)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // 提交按钮 - 使用渐变背景
                val buttonGradient = androidx.compose.ui.graphics.Brush.horizontalGradient(
                    colors = listOf(PrimaryBlue, PrimaryBlueLight)
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp) // 这里可能有行号不一致问题，需要仔细定位代码段
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (selectedOptions.isNotEmpty() && isActive && !isWaiting) buttonGradient 
                            else androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colors = listOf(Color.Gray.copy(alpha = 0.3f), Color.Gray.copy(alpha = 0.3f))
                            )
                        )
                        .clickable(
                            enabled = selectedOptions.isNotEmpty() && isActive && !isWaiting,
                            onClick = { onSubmit(selectedOptions.toList()) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isWaiting) "等待开始" else "提交投票",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (selectedOptions.isNotEmpty() && isActive && !isWaiting) Color.White else Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun WaitingBadge(seconds: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                 androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(WarningOrange, Color(0xFFFF6F00))
                )
            )
            .padding(vertical = 12.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "距离开始还有 ${seconds} 秒",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun CountdownBadge(seconds: Int) {
    val minutes = seconds / 60
    val secs = seconds % 60
    val isUrgent = seconds <= 10
    
    // 紧急状态的脉动动画
    val scale by animateFloatAsState(
        targetValue = if (isUrgent) 1.05f else 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(500),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        )
    )

    val backgroundColor = if (isUrgent) {
        androidx.compose.ui.graphics.Brush.linearGradient(
            colors = listOf(WarningOrange, Color(0xFFFF6F00))
        )
    } else {
        androidx.compose.ui.graphics.Brush.linearGradient(
            colors = listOf(PrimaryBlue, PrimaryBlueLight)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .padding(vertical = 12.dp, horizontal = 16.dp)
            .then(if (isUrgent) Modifier.graphicsLayer(scaleX = scale, scaleY = scale) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = String.format("%02d:%02d", minutes, secs),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            if (isUrgent) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "即将结束",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Text(
            text = text,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun VoteOptionCard(
    option: VoteOption,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    // 点击缩放动画
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(100)
    )
    
    val borderBrush = if (isSelected) {
        androidx.compose.ui.graphics.Brush.linearGradient(
            colors = listOf(PrimaryBlue, PrimaryBlueLight)
        )
    } else {
        null
    }
    
    val backgroundColor = if (isSelected) {
        androidx.compose.ui.graphics.Brush.linearGradient(
            colors = listOf(
                PrimaryBlue.copy(alpha = 0.08f),
                PrimaryBlueLight.copy(alpha = 0.05f)
            )
        )
    } else {
        androidx.compose.ui.graphics.Brush.linearGradient(
            colors = listOf(CardBackground, CardBackground)
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (borderBrush != null) {
                    Modifier.border(
                        width = 2.dp,
                        brush = borderBrush,
                        shape = RoundedCornerShape(16.dp)
                    )
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = Color.LightGray.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            )
            .clickable(enabled = enabled) {
                isPressed = !isPressed
                onClick()
            },
        color = Color.Transparent,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 选择指示器
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            brush = if (isSelected) {
                                androidx.compose.ui.graphics.Brush.linearGradient(
                                    colors = listOf(PrimaryBlue, PrimaryBlueLight)
                                )
                            } else {
                                androidx.compose.ui.graphics.Brush.linearGradient(
                                    colors = listOf(Color.Transparent, Color.Transparent)
                                )
                            },
                            shape = CircleShape
                        )
                        .border(
                            width = 2.dp,
                            color = if (isSelected) Color.Transparent else Color.LightGray,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                
                Text(
                    text = option.content,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) PrimaryBlue else Color.DarkGray
                )
            }
        }
    }
}

@Composable
private fun VoteResultView(result: VoteResult) {
    // 按票数排序以确定排名
    val sortedResults = result.results.sortedByDescending { it.count }
    
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        sortedResults.forEachIndexed { index, optResult ->
            val animatedPercent by animateFloatAsState(
                targetValue = optResult.percent / 100f,
                animationSpec = tween(durationMillis = 800, delayMillis = index * 100)
            )
            
            // 排名图标和颜色
            val rankIcon = when (index) {
                0 -> "🥇"
                1 -> "🥈"
                2 -> "🥉"
                else -> null
            }
            
            val barColor = when (index) {
                0 -> androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(Color(0xFFFFD700), Color(0xFFFFA500))
                )
                1 -> androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(Color(0xFFC0C0C0), Color(0xFF999999))
                )
                2 -> androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(Color(0xFFCD7F32), Color(0xFFB87333))
                )
                else -> androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(PrimaryBlue, PrimaryBlueLight)
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CardBackground,
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rankIcon?.let { icon ->
                                Text(
                                    text = icon,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Text(
                                text = optResult.content,
                                fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Medium,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        Text(
                            text = "${optResult.count}票 (${optResult.percent.toInt()}%)",
                            color = Color.Gray,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    
                    // 渐变进度条
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color.LightGray.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animatedPercent)
                                .background(barColor, RoundedCornerShape(7.dp))
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = PrimaryBlue.copy(alpha = 0.1f)
        ) {
            Text(
                text = "共 ${result.total_voters} 人参与投票",
                color = PrimaryBlue,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )
        }
    }
}
