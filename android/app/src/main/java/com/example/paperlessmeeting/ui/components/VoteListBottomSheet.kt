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
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
 * 投票列表选择器 BottomSheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoteListBottomSheet(
    votes: List<Vote>,
    onVoteSelected: (Vote) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
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
            // 标题
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.HowToVote,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "选择投票",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "共 ${votes.size} 个投票",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // 投票列表
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = 500.dp)
            ) {
                items(votes) { vote ->
                    VoteListItem(
                        vote = vote,
                        onClick = { onVoteSelected(vote) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VoteListItem(
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
        shadowElevation = if (vote.status == "active") 2.dp else 1.dp
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
