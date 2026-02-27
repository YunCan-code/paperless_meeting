package com.example.paperlessmeeting.ui.screens.adaptive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.paperlessmeeting.domain.model.Meeting
import com.example.paperlessmeeting.ui.components.MeetingCard
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
fun MeetingListContent(
    meetings: List<Meeting>,
    onMeetingClick: (Int) -> Unit,
    selectedId: Int? = null,
    isLoadingMore: Boolean = false,
    hasMoreData: Boolean = true,
    onLoadMore: () -> Unit = {}
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
    
    // Calculate initial scroll index
    val initialScrollIndex = remember(groupedMeetings) {
        var index = 0
        val today = LocalDate.now()
        var targetIndex = 0
        
        for ((_, list) in groupedMeetings) {
            var isTargetGroup = false
            try {
                val cleanStart = list.first().startTime.replace(" ", "T")
                val meetingDate = LocalDate.parse(cleanStart.take(10))
                if (!meetingDate.isAfter(today)) {
                    isTargetGroup = true
                }
            } catch(e: Exception) {}

            if (isTargetGroup) {
                targetIndex = index
                break
            }
            index += (1 + list.size)
        }
        targetIndex
    }
    
    // Use key to recreate state if initial index changes (e.g. from 0 on empty load to N on full load)
    val listState = androidx.compose.runtime.key(initialScrollIndex) {
        rememberLazyListState(initialFirstVisibleItemIndex = initialScrollIndex)
    }
    
    // Infinite Scroll Detection
    LaunchedEffect(listState, hasMoreData) {
        snapshotFlow { 
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            
            // Trigger load more when within 3 items of the end
            lastVisibleItem >= totalItems - 3
        }
        .distinctUntilChanged()
        .filter { it && hasMoreData }
        .collect {
            onLoadMore()
        }
    }

    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
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
            
            itemsIndexed(list, key = { index, meeting -> "${meeting.id}_${header}_$index" }) { _, meeting ->
                MeetingCard(
                    meeting = meeting,
                    onClick = { onMeetingClick(meeting.id) }
                )
            }
        }
        
        // Loading More Indicator
        item {
            AnimatedVisibility(
                visible = isLoadingMore,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
        
        // End of List Indicator
        item {
            if (!hasMoreData && meetings.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "已加载全部会议",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        item {
             Spacer(modifier = Modifier.height(48.dp))
        }
    }



        // Top Hint: Future (Show when we can scroll up/backward)
        AnimatedVisibility(
            visible = listState.isScrollInProgress && listState.canScrollBackward,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
        ) {
            androidx.compose.material3.Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                shadowElevation = 6.dp
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("下滑查看未来会议", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Bottom Hint: History (Show when we can scroll down/forward)
        AnimatedVisibility(
            visible = listState.isScrollInProgress && listState.canScrollForward,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
        ) {
            androidx.compose.material3.Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                shadowElevation = 6.dp
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("上滑查看历史会议", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
