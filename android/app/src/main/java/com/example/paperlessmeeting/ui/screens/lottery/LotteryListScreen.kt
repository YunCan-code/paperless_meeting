package com.example.paperlessmeeting.ui.screens.lottery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.paperlessmeeting.domain.model.Meeting
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LotteryListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (Int, String) -> Unit,
    viewModel: LotteryViewModel = hiltViewModel()
) {
    val meetings by viewModel.meetings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("选择正在进行的抽签") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            if (meetings.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无进行中的会议抽签", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(meetings) { meeting ->
                        LotteryMeetingCard(meeting) {
                            onNavigateToDetail(meeting.id, meeting.title)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LotteryMeetingCard(meeting: Meeting, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = meeting.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "会议时间: ${formatTime(meeting.startTime)}",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge(containerColor = MaterialTheme.colorScheme.primary) {
                    Text("进行中", modifier = Modifier.padding(horizontal = 4.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "点击参与抽签",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

fun formatTime(timeStr: String): String {
    return try {
        val clean = timeStr.replace(" ", "T")
        val parsed = LocalDateTime.parse(clean)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        parsed.format(formatter)
    } catch (e: Exception) {
        if (timeStr.length >= 16) {
             timeStr.replace("T", " ").substring(0, 16)
        } else {
             timeStr
        }
    }
}
