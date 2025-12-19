package com.example.paperlessmeeting.ui.screens.adaptive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.paperlessmeeting.domain.model.Meeting
import com.example.paperlessmeeting.ui.components.MeetingCard
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun MeetingListContent(
    meetings: List<Meeting>,
    onMeetingClick: (Int) -> Unit,
    selectedId: Int? = null 
) {
    // Grouping Logic
    val groupedMeetings = remember(meetings) {
        meetings
            .sortedByDescending { it.startTime } // Future (Top) -> Past (Bottom)
            .groupBy { meeting ->
                try {
                    val cleanStart = meeting.startTime.replace(" ", "T")
                    val dateStr = cleanStart.take(10)
                    val date = LocalDate.parse(dateStr)
                    val today = LocalDate.now()
                    
                    when {
                        date.isEqual(today) -> "今天"
                        date.isEqual(today.minusDays(1)) -> "昨天"
                        date.isEqual(today.plusDays(1)) -> "明天"
                        date.year == today.year -> date.format(DateTimeFormatter.ofPattern("M月d日"))
                        else -> date.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
                    }
                } catch (e: Exception) {
                    "其他时间"
                }
            }
    }
    
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    
    // Auto-Scroll to "Today" or nearest Past
    androidx.compose.runtime.LaunchedEffect(groupedMeetings) {
        var index = 0
        val today = LocalDate.now()
        
        for ((_, list) in groupedMeetings) {
            var isTargetGroup = false
            try {
                 val cleanStart = list.first().startTime.replace(" ", "T")
                 val meetingDate = LocalDate.parse(cleanStart.take(10))
                 // Since sorted Descending: Future items come first.
                 // We want to skip Future items and land on Today (or first Past item if Today missing).
                 if (!meetingDate.isAfter(today)) {
                     isTargetGroup = true
                 }
            } catch(e: Exception) {}

            if (isTargetGroup) {
                listState.scrollToItem(index)
                break
            }
            
            // Increment index: 1 for Header + N for Items
            index += (1 + list.size)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        groupedMeetings.forEach { (header, list) ->
            item {
                Text(
                    text = header,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
                )
            }
            
            items(list) { meeting ->
                MeetingCard(
                    meeting = meeting,
                    onClick = { onMeetingClick(meeting.id) }
                )
            }
        }
        
        item {
             Spacer(modifier = Modifier.height(48.dp))
        }
    }
}
