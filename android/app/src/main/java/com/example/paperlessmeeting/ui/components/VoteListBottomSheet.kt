package com.example.paperlessmeeting.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.paperlessmeeting.domain.model.Vote

private val PrimaryBlue = Color(0xFF1976D2)
private val SuccessGreen = Color(0xFF4CAF50)
private val WarmGray = Color(0xFF9E9E9E)
private val WarmBackground = Color(0xFFFAFBFC)
private val CardBackground = Color(0xFFFFFFFF)

/**
 * 投票列表选择器 BottomSheet (支持历史记录)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoteListBottomSheet(
    activeVotes: List<Vote>,
    historyVotes: List<Vote>,
    onTabChange: (Int) -> Unit,
    onVoteSelected: (Vote) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    var selectedTabIndex by remember { mutableStateOf(0) }

    // 当切换到历史记录时，触发回调加载数据
    LaunchedEffect(selectedTabIndex) {
        onTabChange(selectedTabIndex)
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
                .padding(bottom = 32.dp)
        ) {
            // Tabs
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = WarmBackground,
                contentColor = PrimaryBlue,
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = PrimaryBlue
                    )
                }
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("进行中 (${activeVotes.size})") },
                    icon = { Icon(Icons.Default.HowToVote, contentDescription = null) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("历史记录") },
                    icon = { Icon(Icons.Default.History, contentDescription = null) }
                )
            }

            Spacer(Modifier.height(16.dp))

            // Content
            val currentVotes = if (selectedTabIndex == 0) activeVotes else historyVotes
            
            if (currentVotes.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text("暂无数据", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    modifier = Modifier.heightIn(max = 500.dp)
                ) {
                    items(currentVotes) { vote ->
                        VoteListItem(
                            vote = vote,
                            onClick = { onVoteSelected(vote) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VoteListItem(
    vote: Vote,
    onClick: () -> Unit
) {
    val statusColor = when (vote.status) {
        "active" -> PrimaryBlue
        "closed" -> SuccessGreen
        else -> WarmGray
    }
    
    val statusText = when (vote.status) {
        "active" -> "进行中"
        "closed" -> "已结束"
        else -> "草稿"
    }
    
    val statusIcon = when (vote.status) {
        "active" -> Icons.Default.Schedule
        "closed" -> Icons.Default.CheckCircle
        else -> Icons.Default.HowToVote
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                color = if (vote.status == "active") PrimaryBlue.copy(alpha = 0.5f) else Color.LightGray.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick),
        color = if (vote.status == "active") PrimaryBlue.copy(alpha = 0.05f) else CardBackground,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 状态图标
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = statusColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            // 投票信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = vote.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                vote.description?.let { desc ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        maxLines = 2
                    )
                }
                // 显示创建时间或截止时间
               Spacer(Modifier.height(4.dp))
               Text(
                   text = vote.created_at.take(10), // Simple date
                   style = MaterialTheme.typography.labelSmall,
                   color = Color.LightGray
               )
            }

            Spacer(Modifier.width(12.dp))

            // 状态标签
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = statusColor.copy(alpha = 0.1f)
            ) {
                Text(
                    text = statusText,
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
