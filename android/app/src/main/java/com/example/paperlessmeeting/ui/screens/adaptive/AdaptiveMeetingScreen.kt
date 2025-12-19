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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
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
    
    // Filter Meetings
    val allMeetings = if (uiState is com.example.paperlessmeeting.ui.screens.home.HomeUiState.Success) {
        (uiState as com.example.paperlessmeeting.ui.screens.home.HomeUiState.Success).meetings
    } else emptyList()

    // UI Logic State
    // Default filter: If nav param is not "ALL", use it as initial filter
    var selectedTypeFilter by remember(meetingTypeName) { 
        mutableStateOf(if (meetingTypeName == "ALL") null else meetingTypeName) 
    }
    
    var selectedMeetingId by remember { mutableStateOf<Int?>(null) }
    var fullMeeting by remember { mutableStateOf<Meeting?>(null) }
    
    // Fetch full details when ID changes
    LaunchedEffect(selectedMeetingId) {
        if (selectedMeetingId != null) {
            fullMeeting = viewModel.getMeetingDetails(selectedMeetingId!!)
        } else {
            fullMeeting = null
        }
    }

    // Dynamic Types from Data
    val availableTypes = remember(allMeetings) {
        allMeetings.mapNotNull { it.meetingTypeName }.filter { it.isNotEmpty() }.distinct().sorted()
    }

    val filteredMeetings = if (selectedTypeFilter == null) {
        allMeetings
    } else {
        allMeetings.filter { it.meetingTypeName == selectedTypeFilter }
    }
    
    val selectedMeeting = allMeetings.find { it.id == selectedMeetingId } 
    // Removed auto-select behavior as requested so user sees prompt first

    BoxWithConstraints {
        val isTablet = maxWidth > 600.dp
        
        if (!isTablet) {
            // === PHONE LAYOUT ===
            Column(modifier = Modifier.fillMaxSize()) {
                FilterChipsBar(
                    availableTypes = availableTypes,
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
                    color = MaterialTheme.colorScheme.surface, 
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
                            availableTypes = availableTypes,
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
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Logic: Show Full Details if loaded, else show List Data (partial), else Empty
                    val displayMeeting = fullMeeting ?: selectedMeeting
                    
                    if (displayMeeting != null) {
                        MeetingDetailContent(
                            meeting = displayMeeting,
                            onAttachmentClick = { url, name ->
                                 val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
                                 val encodedName = java.net.URLEncoder.encode(name, "UTF-8")
                                 navController.navigate("reader?url=$encodedUrl&name=$encodedName")
                            }
                        )
                    } else {
                        EmptyDetailView()
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FilterChipsBar(
    availableTypes: List<String>,
    selectedType: String?,
    onTypeSelected: (String?) -> Unit
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
        items(availableTypes) { typeName ->
            val color = com.example.paperlessmeeting.ui.components.generateThemeColor(typeName)
            
            androidx.compose.material3.FilterChip(
                selected = selectedType == typeName,
                onClick = { onTypeSelected(if (selectedType == typeName) null else typeName) },
                label = { androidx.compose.material3.Text(typeName) },
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    selectedContainerColor = color.copy(alpha = 0.2f),
                    selectedLabelColor = color,
                    labelColor = androidx.compose.ui.graphics.Color.Gray
                ),
                border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
                    enabled = true, 
                    selected = selectedType == typeName,
                    borderColor = if (selectedType == typeName) color else androidx.compose.ui.graphics.Color.Transparent
                )
            )
        }
    }
}

@Composable
fun EmptyDetailView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    androidx.compose.foundation.shape.CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.DateRange,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
        }
        
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "暂无选中会议",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "请从左侧列表选择一项以查看详情",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
