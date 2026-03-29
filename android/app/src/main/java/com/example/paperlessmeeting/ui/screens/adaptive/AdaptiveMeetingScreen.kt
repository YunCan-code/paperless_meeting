package com.example.paperlessmeeting.ui.screens.adaptive
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.paperlessmeeting.domain.model.Meeting
import com.example.paperlessmeeting.ui.components.notice.LocalAppNoticeController
import com.example.paperlessmeeting.ui.components.generateThemeColor
import com.example.paperlessmeeting.ui.navigation.MAIN_TABS_ROUTE
import com.example.paperlessmeeting.ui.navigation.requestMainTabTransition
import com.example.paperlessmeeting.ui.screens.detail.DetailOverlayTopBar
import com.example.paperlessmeeting.ui.screens.detail.MeetingDetailContent
import com.example.paperlessmeeting.ui.screens.home.HomeUiState
import com.example.paperlessmeeting.ui.screens.home.HomeViewModel
import com.example.paperlessmeeting.ui.screens.home.SplitDetailCheckInResult
import com.example.paperlessmeeting.utils.Resource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private val DetailPaneShape = androidx.compose.foundation.shape.RoundedCornerShape(
    topStart = 26.dp,
    bottomStart = 26.dp
)

@Composable
fun AdaptiveMeetingScreen(
    meetingTypeName: String,
    navController: NavController,
    initialMeetingId: Int? = null,
    isActive: Boolean = true,
    onNavigateToMedia: (() -> Unit)? = null,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val noticeController = LocalAppNoticeController.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val selectedMeetingId by viewModel.selectedMeetingId.collectAsState()
    val isCheckInSubmitting by viewModel.isCheckInSubmitting.collectAsState()

    val allMeetings = (uiState as? HomeUiState.Success)?.meetings ?: emptyList()
    val isLoadingMore = (uiState as? HomeUiState.Success)?.isLoadingMore ?: false
    val hasMoreData = (uiState as? HomeUiState.Success)?.hasMoreData ?: true

    var selectedTypeFilter by remember(meetingTypeName) {
        mutableStateOf(if (meetingTypeName == "ALL") null else meetingTypeName)
    }
    var fullMeeting by remember { mutableStateOf<Meeting?>(null) }
    var isDetailLoading by remember { mutableStateOf(false) }
    var isDetailRefreshing by remember { mutableStateOf(false) }
    var showCheckInHint by rememberSaveable { mutableStateOf(false) }

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
                is Resource.Success -> {
                    if (selectedMeetingId == meetingId) {
                        fullMeeting = result.data
                    }
                }
                is Resource.Error -> {
                    if (selectedMeetingId != meetingId) {
                        return
                    }
                    if (result.message == "HTTP_404") {
                        fullMeeting = null
                        viewModel.selectMeeting(null)
                        noticeController.showMessage(
                            hiddenMessage,
                            androidx.compose.material3.SnackbarDuration.Long
                        )
                    } else {
                        noticeController.showMessage("刷新会议详情失败：${result.message}")
                    }
                }
                Resource.Loading -> Unit
            }
        } catch (e: CancellationException) {
            throw e
        } finally {
            isDetailLoading = false
            isDetailRefreshing = false
        }
    }

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
        viewModel.actionMessage.collectLatest { message ->
            noticeController.showMessage(message)
        }
    }

    LaunchedEffect(selectedMeetingId) {
        val meetingId = selectedMeetingId
        if (meetingId == null) {
            fullMeeting = null
            isDetailLoading = false
            isDetailRefreshing = false
            showCheckInHint = false
            return@LaunchedEffect
        }

        val shouldShowBlockingLoading = fullMeeting?.id != meetingId
        refreshSelectedMeeting(
            meetingId = meetingId,
            showBlockingLoading = shouldShowBlockingLoading
        )
    }

    LaunchedEffect(fullMeeting?.id, fullMeeting?.isTodayMeeting, fullMeeting?.isCheckedIn) {
        val meeting = fullMeeting
        if (meeting == null || !meeting.isTodayMeeting || meeting.isCheckedIn) {
            showCheckInHint = false
            return@LaunchedEffect
        }

        if (!viewModel.shouldShowCheckInHint(meeting.id)) {
            showCheckInHint = false
            return@LaunchedEffect
        }

        viewModel.markCheckInHintSeen(meeting.id)
        showCheckInHint = true
        delay(5000)
        showCheckInHint = false
    }

    val availableTypes = remember(allMeetings) {
        allMeetings
            .mapNotNull { it.meetingTypeName }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    }

    val filteredMeetings = if (selectedTypeFilter == null) {
        allMeetings
    } else {
        allMeetings.filter { it.meetingTypeName == selectedTypeFilter }
    }

    BoxWithConstraints {
        val isTablet = maxWidth > 600.dp

        BackHandler(enabled = isTablet && selectedMeetingId != null) {
            viewModel.selectMeeting(null)
        }

        if (!isTablet) {
            var hasForwardedInitialMeeting by rememberSaveable(initialMeetingId) {
                mutableStateOf(false)
            }

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
            Row(modifier = Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier.width(360.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp
                ) {
                    Column(modifier = Modifier.statusBarsPadding()) {
                        Box(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "会议列表",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
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
                            isLoadingMore = isLoadingMore,
                            hasMoreData = hasMoreData,
                            onLoadMore = { viewModel.loadMore() }
                        )
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.background,
                    shape = DetailPaneShape,
                    tonalElevation = 1.dp
                ) {
                    when {
                        selectedMeetingId != null && isDetailLoading -> {
                            LoadingDetailView()
                        }

                        fullMeeting != null -> {
                            Box(modifier = Modifier.fillMaxSize()) {
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

                                DetailOverlayTopBar(
                                    currentMeeting = displayMeeting,
                                    currentVote = null,
                                    onVoteClick = {},
                                    onCheckInClick = {
                                        showCheckInHint = false
                                        scope.launch {
                                            when (val result = viewModel.checkInMeeting(displayMeeting.id)) {
                                                is SplitDetailCheckInResult.Updated -> {
                                                    if (selectedMeetingId == result.meeting.id) {
                                                        fullMeeting = result.meeting
                                                    }
                                                }

                                                is SplitDetailCheckInResult.Error -> {
                                                    noticeController.showMessage(result.message)
                                                }

                                                is SplitDetailCheckInResult.Hidden -> {
                                                    fullMeeting = null
                                                    viewModel.selectMeeting(null)
                                                    noticeController.showMessage(
                                                        result.message,
                                                        androidx.compose.material3.SnackbarDuration.Long
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    onCloseClick = { viewModel.selectMeeting(null) },
                                    isCheckInSubmitting = isCheckInSubmitting,
                                    showCheckInHint = showCheckInHint,
                                    enabled = true
                                )

                                if (isDetailRefreshing) {
                                    SplitDetailRefreshingBadge(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(top = 72.dp, end = 24.dp)
                                    )
                                }
                            }
                        }

                        else -> EmptyDetailView()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterChipsBar(
    availableTypes: List<String>,
    selectedType: String?,
    onTypeSelected: (String?) -> Unit
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(Unit) {
        delay(800)
        try {
            listState.animateScrollBy(150f)
            listState.animateScrollBy(-150f)
        } catch (_: Exception) {
        }
    }

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            FilterChip(
                selected = selectedType == null,
                onClick = { onTypeSelected(null) },
                label = { Text("全部") },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = if (selectedType == null) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        androidx.compose.ui.graphics.Color.Transparent
                    },
                    labelColor = if (selectedType == null) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selectedType == null,
                    borderColor = androidx.compose.ui.graphics.Color.Transparent,
                    selectedBorderColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )
        }

        items(availableTypes) { typeName ->
            val color = generateThemeColor(typeName)

            FilterChip(
                selected = selectedType == typeName,
                onClick = { onTypeSelected(if (selectedType == typeName) null else typeName) },
                label = { Text(typeName) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = color.copy(alpha = 0.2f),
                    selectedLabelColor = color,
                    labelColor = androidx.compose.ui.graphics.Color.Gray
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selectedType == typeName,
                    borderColor = if (selectedType == typeName) {
                        color
                    } else {
                        androidx.compose.ui.graphics.Color.Transparent
                    }
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
        Box(
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

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "暂无选中会议",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

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
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "正在加载会议详情",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
