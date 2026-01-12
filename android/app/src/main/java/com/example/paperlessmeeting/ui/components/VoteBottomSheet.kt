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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.paperlessmeeting.domain.model.Vote
import com.example.paperlessmeeting.domain.model.VoteOption
import com.example.paperlessmeeting.domain.model.VoteResult
import kotlinx.coroutines.delay

private val PrimaryBlue = Color(0xFF1976D2)
private val SuccessGreen = Color(0xFF4CAF50)
private val WarmBackground = Color(0xFFFAF8F5)

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
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    // 倒计时
    var remainingSeconds by remember { mutableIntStateOf(vote.remaining_seconds ?: vote.duration_seconds) }
    val isActive = vote.status == "active" && remainingSeconds > 0

    // 选中的选项
    var selectedOptions by remember { mutableStateOf<Set<Int>>(emptySet()) }

    // 倒计时逻辑
    LaunchedEffect(isActive) {
        while (isActive && remainingSeconds > 0) {
            delay(1000)
            remainingSeconds--
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = WarmBackground,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (result != null) Icons.Default.Poll else Icons.Default.HowToVote,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = vote.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f)
                )
                
                // 倒计时或状态
                if (isActive && !hasVoted) {
                    CountdownBadge(remainingSeconds)
                } else if (hasVoted) {
                    Text("已投票", color = SuccessGreen, fontWeight = FontWeight.Medium)
                } else {
                    Text("已结束", color = Color.Gray)
                }
            }

            vote.description?.let { desc ->
                Spacer(Modifier.height(8.dp))
                Text(desc, color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(20.dp))

            if (result != null) {
                // ===== 结果视图 =====
                VoteResultView(result)
            } else if (hasVoted) {
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
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(vote.options) { option ->
                        VoteOptionCard(
                            option = option,
                            isSelected = option.id in selectedOptions,
                            enabled = isActive,
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

                Spacer(Modifier.height(20.dp))

                // 提交按钮
                Button(
                    onClick = { onSubmit(selectedOptions.toList()) },
                    enabled = selectedOptions.isNotEmpty() && isActive,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("提交投票", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun CountdownBadge(seconds: Int) {
    val minutes = seconds / 60
    val secs = seconds % 60
    val color = if (seconds <= 10) Color.Red else PrimaryBlue

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = String.format("%02d:%02d", minutes, secs),
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
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
    val borderColor = if (isSelected) PrimaryBlue else Color.LightGray
    val backgroundColor = if (isSelected) PrimaryBlue.copy(alpha = 0.05f) else Color.White

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Radio/Checkbox indicator
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .border(2.dp, borderColor, CircleShape)
                    .background(if (isSelected) PrimaryBlue else Color.Transparent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = option.content,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun VoteResultView(result: VoteResult) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        result.results.forEach { optResult ->
            val animatedPercent by animateFloatAsState(
                targetValue = optResult.percent / 100f,
                animationSpec = tween(durationMillis = 800)
            )

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(optResult.content, fontWeight = FontWeight.Medium)
                    Text("${optResult.count}票 (${optResult.percent.toInt()}%)", color = Color.Gray)
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { animatedPercent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = PrimaryBlue,
                    trackColor = Color.LightGray.copy(alpha = 0.3f)
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "共 ${result.total_voters} 人参与投票",
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
