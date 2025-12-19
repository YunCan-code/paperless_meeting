package com.example.paperlessmeeting.ui.screens.adaptive

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.paperlessmeeting.domain.model.Meeting
import com.example.paperlessmeeting.domain.model.MeetingType
import com.example.paperlessmeeting.ui.screens.detail.MeetingDetailContent
import com.example.paperlessmeeting.ui.screens.adaptive.MeetingListContent
import com.example.paperlessmeeting.ui.screens.home.HomeViewModel

@Composable
fun AdaptiveMeetingScreen(
    meetingTypeName: String, // Can be "ALL"
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // UI Logic State
    var selectedTypeFilter by remember { 
        mutableStateOf(
            if (meetingTypeName == "ALL") null else try { MeetingType.valueOf(meetingTypeName) } catch(_:Exception){null} 
        ) 
    }
    
    var selectedMeetingId by remember { mutableStateOf<Int?>(null) }
    
    // Filter Meetings
    val allMeetings = if (uiState is com.example.paperlessmeeting.ui.screens.home.HomeUiState.Success) {
        (uiState as com.example.paperlessmeeting.ui.screens.home.HomeUiState.Success).meetings
    } else emptyList()

    val filteredMeetings = if (selectedTypeFilter == null) {
        allMeetings
    } else {
        allMeetings.filter { it.getUiType() == selectedTypeFilter }
    }
    
    val selectedMeeting = allMeetings.find { it.id == selectedMeetingId } 
        ?: filteredMeetings.firstOrNull()

    BoxWithConstraints {
        val isTablet = maxWidth > 600.dp
        
        if (!isTablet) {
            // === PHONE LAYOUT ===
            Column(modifier = Modifier.fillMaxSize()) {
                FilterChipsBar(
                    selectedType = selectedTypeFilter,
                    onTypeSelected = { selectedTypeFilter = it }
                )
                MeetingListContent(
                    meetings = filteredMeetings,
                    onMeetingClick = { meetingId ->
                        navController.navigate("detail/$meetingId")
                    }
                )
            }
        } else {
            // === TABLET/SPLIT LAYOUT ===
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Pane: List
                Surface(
                    modifier = Modifier.width(360.dp),
                    color = MaterialTheme.colorScheme.surface, // Should be slightly different from Rail?
                    tonalElevation = 1.dp
                ) {
                    Column {
                        // Title or Filter Header
                        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(16.dp)) {
                             androidx.compose.material3.Text(
                                 "会议列表", 
                                 style = MaterialTheme.typography.titleLarge, 
                                 fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                             )
                        }
                        
                        FilterChipsBar(
                            selectedType = selectedTypeFilter,
                            onTypeSelected = { selectedTypeFilter = it }
                        )

                        MeetingListContent(
                            meetings = filteredMeetings,
                            onMeetingClick = { meetingId -> selectedMeetingId = meetingId },
                            selectedId = selectedMeetingId ?: selectedMeeting?.id
                        )
                    }
                }
                
                // Right Pane: Detail
                Surface(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.background // #F5F5F7
                ) {
                    if (selectedMeeting != null) {
                        MeetingDetailContent(meeting = selectedMeeting)
                    } else {
                        // Empty State
                         androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                             androidx.compose.material3.Text("Select a meeting", modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
                         }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FilterChipsBar(
    selectedType: MeetingType?,
    onTypeSelected: (MeetingType?) -> Unit
) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            androidx.compose.material3.FilterChip(
                selected = selectedType == null,
                onClick = { onTypeSelected(null) },
                label = { androidx.compose.material3.Text("全部") }
            )
        }
        items(MeetingType.values()) { type ->
            androidx.compose.material3.FilterChip(
                selected = selectedType == type,
                onClick = { onTypeSelected(if (selectedType == type) null else type) },
                label = { androidx.compose.material3.Text(type.displayName) },
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    selectedContainerColor = type.color.copy(alpha = 0.2f),
                    selectedLabelColor = type.color
                )
            )
        }
    }
}
