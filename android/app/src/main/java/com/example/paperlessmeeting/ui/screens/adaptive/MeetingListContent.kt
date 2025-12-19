package com.example.paperlessmeeting.ui.screens.adaptive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.paperlessmeeting.domain.model.Meeting
import com.example.paperlessmeeting.ui.components.MeetingCard

@Composable
fun MeetingListContent(
    meetings: List<Meeting>,
    onMeetingClick: (Int) -> Unit,
    selectedId: Int? = null // For highlighting in split view
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(meetings) { meeting ->
            // In a real app we might pass 'isSelected' to MeetingCard to show a highlight border
            MeetingCard(
                meeting = meeting,
                onClick = { onMeetingClick(meeting.id) }
            )
        }
    }
}
