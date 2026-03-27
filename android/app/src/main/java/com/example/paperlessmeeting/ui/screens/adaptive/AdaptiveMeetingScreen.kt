package com.example.paperlessmeeting.ui.screens.adaptive

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.compose.foundation.gestures.animateScrollBy
import com.example.paperlessmeeting.domain.model.Meeting
import com.example.paperlessmeeting.domain.model.MeetingType
import com.example.paperlessmeeting.ui.navigation.MAIN_TABS_ROUTE
import com.example.paperlessmeeting.ui.navigation.requestMainTabTransition
import com.example.paperlessmeeting.ui.screens.detail.MeetingDetailContent
import com.example.paperlessmeeting.ui.screens.adaptive.MeetingListContent
import com.example.paperlessmeeting.ui.screens.home.HomeViewModel

private val DetailPaneShape = androidx.compose.foundation.shape.RoundedCornerShape(
    topStart = 26.dp,
    bottomStart = 26.dp
)

@Composable
fun AdaptiveMeetingScreen(
    meetingTypeName: String, // Can be "ALL"
    navController: NavController,
    initialMeetingId: Int? = null,
    isActive: Boolean = true,
    onNavigateToMedia: (() -> Unit)? = null,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    
    // Filter Meetings & Pagination State
    val allMeetings = if (uiState is com.example.paperlessmeeting.ui.screens.home.HomeUiState.Success) {
        (uiState as com.example.paperlessmeeting.ui.screens.home.HomeUiState.Success).meetings
    } else emptyList()
    
    val isLoadingMore = if (uiState is com.example.paperlessmeeting.ui.screens.home.HomeUiState.Success) {
        (uiState as com.example.paperlessmeeting.ui.screens.home.HomeUiState.Success).isLoadingMore
    } else false
    
    val hasMoreData = if (uiState is com.example.paperlessmeeting.ui.screens.home.HomeUiState.Success) {
        (uiState as com.example.paperlessmeeting.ui.screens.home.HomeUiState.Success).hasMoreData
    } else true

    // UI Logic State
    // Default filter: If nav param is not "ALL", use it as initial filter
    var selectedTypeFilter by remember(meetingTypeName) { 
        mutableStateOf(if (meetingTypeName == "ALL") null else meetingTypeName) 
    }
    
    val selectedMeetingId by viewModel.selectedMeetingId.collectAsState()
    
    // Handle Initial Link
    LaunchedEffect(initialMeetingId) {
        if (initialMeetingId != null) {
            viewModel.selectMeeting(initialMeetingId)
        }
    }

    LaunchedEffect(isActive) {
        if (isActive) {
            viewModel.refreshOnVisible()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.actionMessage.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    var fullMeeting by remember { mutableStateOf<Meeting?>(null) }
    var isDetailLoading by remember { mutableStateOf(false) }
    var isDetailRefreshing by remember { mutableStateOf(false) }

    suspend fun refreshSelectedMeeting(
        meetingId: Int,
        showBlockingLoading: Boolean,
        hiddenMessage: String = "该会议已不可见"
    ) {
        if (showBlockingLoading) {
            isDetailLoading = true
        } else {
            isDetailRefreshing = true
        }

        try {
            when (val result = viewModel.getMeetingDetailsResult(meetingId)) {
                is com.example.paperlessmeeting.utils.Resource.Success -> {
                    fullMeeting = result.data
                }
                is com.example.paperlessmeeting.utils.Resource.Error -> {
                    if (result.message == "HTTP_404") {
                        fullMeeting = null
                        viewModel.selectMeeting(null)
                        Toast.makeText(context, hiddenMessage, Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(
                            context,
                            "刷新会议详情失败：${result.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                com.example.paperlessmeeting.utils.Resource.Loading -> Unit
            }
        } finally {
            isDetailLoading = false
            isDetailRefreshing = false
        }
    }

    LaunchedEffect(selectedMeetingId) {
        val meetingId = selectedMeetingId
        if (meetingId == null) {
            fullMeeting = null
            isDetailLoading = false
            isDetailRefreshing = false
            return@LaunchedEffect
        }

        val shouldShowBlockingLoading = fullMeeting?.id != meetingId
        refreshSelectedMeeting(
            meetingId = meetingId,
            showBlockingLoading = shouldShowBlockingLoading
        )
    }

    LaunchedEffect(allMeetings) {
        val meetingId = selectedMeetingId ?: return@LaunchedEffect
        if (isDetailLoading || fullMeeting?.id != meetingId) {
            return@LaunchedEffect
        }

        refreshSelectedMeeting(
            meetingId = meetingId,
            showBlockingLoading = false
        )
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

        BackHandler(enabled = isTablet && selectedMeetingId != null) {
            viewModel.selectMeeting(null)
        }
        
        if (!isTablet) {
            var hasForwardedInitialMeeting by rememberSaveable(initialMeetingId) {
                mutableStateOf(false)
            }

            // === PHONE LAYOUT ===
            // Auto-forward to detail if initial ID is present (and we haven't handled it yet)
            // Note: ideally we consume the event.
            LaunchedEffect(initialMeetingId, hasForwardedInitialMeeting) {
                if (initialMeetingId != null && !hasForwardedInitialMeeting) {
                    hasForwardedInitialMeeting = true
                    navController.navigate("detail/$initialMeetingId")
                }
            }

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
                    },
                    isLoadingMore = isLoadingMore,
                    hasMoreData = hasMoreData,
                    onLoadMore = { viewModel.loadMore() }
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
                    Column(modifier = Modifier.statusBarsPadding()) {
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
                            onMeetingClick = { meetingId -> viewModel.selectMeeting(meetingId) },
                            selectedId = selectedMeetingId ?: selectedMeeting?.id,
                            isLoadingMore = isLoadingMore,
                            hasMoreData = hasMoreData,
                            onLoadMore = { viewModel.loadMore() }
                        )
                    }
                }
                
                // Right Pane: Detail
                Surface(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.background,
                    shape = DetailPaneShape,
                    tonalElevation = 1.dp
                ) {
                    if (selectedMeetingId != null && isDetailLoading) {
                        LoadingDetailView()
                    } else if (fullMeeting != null) {
                        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                            val displayMeeting = fullMeeting!!
                            MeetingDetailContent(
                                meeting = displayMeeting,
                                staticBaseUrl = viewModel.staticBaseUrl,
                                onAttachmentClick = { url, name ->
                                     val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
                                     val encodedName = java.net.URLEncoder.encode(name, "UTF-8")
                                     navController.navigate("reader?url=$encodedUrl&name=$encodedName")
                                },
                                onMediaClick = {
                                    if (onNavigateToMedia != null) {
                                        onNavigateToMedia()
                                    } else {
                                        navController.requestMainTabTransition(2)
                                        navController.popBackStack(MAIN_TABS_ROUTE, inclusive = false)
                                    }
                                }
                            )
                            // Close Button Overlay (Top Right)
                            androidx.compose.material3.IconButton(
                                onClick = { viewModel.selectMeeting(null) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp)
                            ) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Close,
                                    contentDescription = "关闭详情",
                                    tint = androidx.compose.ui.graphics.Color.White
                                )
                            }

                            if (isDetailRefreshing) {
                                SplitDetailRefreshingBadge(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 72.dp, end = 24.dp)
                                )
                            }
                        }
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
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    
    // Horizontal Scroll Hint Animation
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(800)
        // Peek right and back
        try {
            listState.animateScrollBy(150f)
            listState.animateScrollBy(-150f)
        } catch (e: Exception) {
            // Ignore animation errors
        }
    }

    androidx.compose.foundation.lazy.LazyRow(
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            androidx.compose.material3.FilterChip(
                selected = selectedType == null,
                onClick = { onTypeSelected(null) },
                label = { androidx.compose.material3.Text("全部") },
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    containerColor = if (selectedType == null) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                    labelColor = if (selectedType == null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selectedType == null,
                    borderColor = androidx.compose.ui.graphics.Color.Transparent,
                    selectedBorderColor = androidx.compose.ui.graphics.Color.Transparent
                )
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
private fun SplitDetailRefreshingBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 2.dp,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp
            )
            Text(
                text = "正在刷新",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
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

@Composable
fun LoadingDetailView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "正在加载会议详情",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
