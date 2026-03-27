package com.example.paperlessmeeting.ui.screens.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import com.example.paperlessmeeting.ui.components.PdfThumbnail
import java.io.File
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.paperlessmeeting.domain.model.Meeting
import com.example.paperlessmeeting.ui.components.MeetingStatusBadge
import com.example.paperlessmeeting.ui.components.notice.LocalAppNoticeController
import com.example.paperlessmeeting.ui.components.generateThemeColor
import com.example.paperlessmeeting.ui.components.image.AppImageSlot
import com.example.paperlessmeeting.ui.components.image.MeetingCoverImage
import com.example.paperlessmeeting.ui.navigation.MAIN_TABS_ROUTE
import com.example.paperlessmeeting.ui.navigation.requestMainTabTransition
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay

private const val DETAIL_TO_MEDIA_EXIT_DURATION_MS = 240
private val DetailHeroShape = RoundedCornerShape(
    topStart = 0.dp,
    topEnd = 0.dp,
    bottomStart = 22.dp,
    bottomEnd = 22.dp
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    navController: NavController,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val noticeController = LocalAppNoticeController.current
    val uiState by viewModel.uiState.collectAsState()
    val currentVote by viewModel.currentVote.collectAsState()
    val voteResult by viewModel.voteResult.collectAsState()
    val hasVoted by viewModel.hasVoted.collectAsState()
    val showVoteSheet by viewModel.showVoteSheet.collectAsState()
    val isCheckInSubmitting by viewModel.isCheckInSubmitting.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var isNavigatingToMedia by rememberSaveable { mutableStateOf(false) }
    var showCheckInHint by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isNavigatingToMedia) {
        if (!isNavigatingToMedia) {
            return@LaunchedEffect
        }

        delay(DETAIL_TO_MEDIA_EXIT_DURATION_MS.toLong())
        navController.requestMainTabTransition(2)
        val popped = navController.popBackStack(MAIN_TABS_ROUTE, inclusive = false)
        if (!popped) {
            isNavigatingToMedia = false
        }
    }

    LaunchedEffect(Unit) {
        viewModel.actionMessage.collectLatest { message ->
            noticeController.showMessage(message)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.exitDetail.collectLatest { message ->
            noticeController.showMessage(
                message,
                androidx.compose.material3.SnackbarDuration.Long
            )
            navController.popBackStack()
        }
    }

    LaunchedEffect(
        (uiState as? DetailUiState.Success)?.meeting?.id,
        (uiState as? DetailUiState.Success)?.meeting?.isTodayMeeting,
        (uiState as? DetailUiState.Success)?.meeting?.isCheckedIn
    ) {
        val meeting = (uiState as? DetailUiState.Success)?.meeting
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

    Scaffold { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (val state = uiState) {
                is DetailUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    DetailOverlayTopBar(
                        currentMeeting = null,
                        currentVote = currentVote,
                        onVoteClick = viewModel::openVoteSheet,
                        onCloseClick = { navController.popBackStack() },
                        onCheckInClick = {},
                        isCheckInSubmitting = false,
                        showCheckInHint = false,
                        enabled = true
                    )
                }

                is DetailUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Error: ${state.message}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    DetailOverlayTopBar(
                        currentMeeting = null,
                        currentVote = currentVote,
                        onVoteClick = viewModel::openVoteSheet,
                        onCloseClick = { navController.popBackStack() },
                        onCheckInClick = {},
                        isCheckInSubmitting = false,
                        showCheckInHint = false,
                        enabled = true
                    )
                }

                is DetailUiState.Success -> {
                    AnimatedVisibility(
                        visible = !isNavigatingToMedia,
                        enter = androidx.compose.animation.EnterTransition.None,
                        exit = slideOutHorizontally(
                            animationSpec = tween(
                                durationMillis = DETAIL_TO_MEDIA_EXIT_DURATION_MS,
                                easing = FastOutSlowInEasing
                            ),
                            targetOffsetX = { fullWidth -> -(fullWidth * 0.24f).toInt() }
                        ) + fadeOut(
                            animationSpec = tween(durationMillis = DETAIL_TO_MEDIA_EXIT_DURATION_MS)
                        )
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            MeetingDetailContent(
                                meeting = state.meeting,
                                staticBaseUrl = viewModel.staticBaseUrl,
                                onAttachmentClick = { url, name ->
                                    val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
                                    val encodedName = java.net.URLEncoder.encode(name, "UTF-8")
                                    navController.navigate("reader?url=$encodedUrl&name=$encodedName")
                                },
                                isMediaNavigating = isNavigatingToMedia,
                                onMediaClick = {
                                    if (!isNavigatingToMedia) {
                                        isNavigatingToMedia = true
                                    }
                                }
                            )

                            DetailOverlayTopBar(
                                currentMeeting = state.meeting,
                                currentVote = currentVote,
                                onVoteClick = viewModel::openVoteSheet,
                                onCheckInClick = {
                                    showCheckInHint = false
                                    viewModel.checkIn()
                                },
                                onCloseClick = { navController.popBackStack() },
                                isCheckInSubmitting = isCheckInSubmitting,
                                showCheckInHint = showCheckInHint,
                                enabled = !isNavigatingToMedia
                            )

                            if (isRefreshing) {
                                DetailRefreshingBadge(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 72.dp, end = 20.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (showVoteSheet && currentVote != null) {
                com.example.paperlessmeeting.ui.components.VoteBottomSheet(
                    vote = currentVote!!,
                    hasVoted = hasVoted,
                    result = voteResult,
                    onSubmit = { optionIds ->
                        viewModel.submitVote(optionIds)
                    },
                    onDismiss = {
                        viewModel.dismissVoteSheet()
                    },
                    onFetchResult = { voteId ->
                        viewModel.fetchVoteResult(voteId)
                    }
                )
            }
        }
    }
}

@Composable
private fun DetailRefreshingBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
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
                text = "\u6b63\u5728\u5237\u65b0",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun MeetingDetailContent(
    meeting: Meeting,
    staticBaseUrl: String,
    onAttachmentClick: (String, String) -> Unit,
    isMediaNavigating: Boolean = false,
    onMediaClick: () -> Unit
) {
    val context = LocalContext.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val isPhone = screenWidthDp < 600
    val isWideScreen = screenWidthDp > 700
    val heroHeight = if (isPhone) 200.dp else 280.dp
    val heroHeightPx = with(LocalDensity.current) { heroHeight.toPx() }
    val scrollState = rememberScrollState()
    val heroAccentColor = remember(meeting.meetingTypeName, meeting.title) {
        generateThemeColor(meeting.meetingTypeName ?: meeting.title)
    }

    val collapseProgress by remember {
        derivedStateOf { (scrollState.value / heroHeightPx).coerceIn(0f, 1f) }
    }

    val EmptySection: @Composable (String) -> Unit = { text ->
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Inbox,
                contentDescription = null,
                tint = Color.LightGray.copy(alpha = 0.5f),
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(text, color = Color.LightGray, style = MaterialTheme.typography.bodyMedium)
        }
    }

    var attendeesExpanded by remember { mutableStateOf(false) }
    val maxVisibleAttendees = 5

    val InfoSectionContent: @Composable () -> Unit = {
        SectionHeader(title = "\u53c2\u4f1a\u4eba\u5458")
        Spacer(modifier = Modifier.height(8.dp))

        val attendeesList = meeting.attendees?.sortedBy {
            when (it.meetingRole) {
                "\u4e3b\u8bb2\u4eba" -> 0
                "\u7279\u9080\u5609\u5bbe" -> 1
                "\u53c2\u4f1a\u4eba\u5458" -> 2
                else -> 3
            }
        } ?: emptyList()

        if (attendeesList.isNotEmpty()) {
            val displayList = if (attendeesExpanded || attendeesList.size <= maxVisibleAttendees) {
                attendeesList
            } else {
                attendeesList.take(maxVisibleAttendees)
            }
            val remainingCount = attendeesList.size - maxVisibleAttendees

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                displayList.forEach { attendee ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "\u2022 ${attendee.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (attendee.meetingRole != "\u53c2\u4f1a\u4eba\u5458") {
                            androidx.compose.material3.Surface(
                                color = when (attendee.meetingRole) {
                                    "\u4e3b\u8bb2\u4eba" -> Color(0xFFFFF3E0)
                                    "\u7279\u9080\u5609\u5bbe" -> Color(0xFFF3E5F5)
                                    else -> Color(0xFFE3F2FD)
                                },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = attendee.meetingRole,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (attendee.meetingRole) {
                                        "\u4e3b\u8bb2\u4eba" -> Color(0xFFE65100)
                                        "\u7279\u9080\u5609\u5bbe" -> Color(0xFF4A148C)
                                        else -> Color(0xFF0D47A1)
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
                if (!attendeesExpanded && remainingCount > 0) {
                    Text(
                        text = "...\u8fd8\u6709 $remainingCount \u4eba\uff0c\u70b9\u51fb\u5c55\u5f00",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { attendeesExpanded = true }
                            .padding(vertical = 4.dp)
                    )
                } else if (attendeesExpanded && attendeesList.size > maxVisibleAttendees) {
                    Text(
                        text = "\u6536\u8d77",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { attendeesExpanded = false }
                            .padding(vertical = 4.dp)
                    )
                }
            }
        } else if (!meeting.speaker.isNullOrEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("\u4e3b\u8bb2\u4eba\uff1a", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text(meeting.speaker)
            }
        } else {
            EmptySection("\u6682\u65e0\u53c2\u4f1a\u4eba\u5458\u4fe1\u606f")
        }
    }
    val AgendaSectionContent: @Composable () -> Unit = {
        val normalizedAgendaItems = extractAgendaItems(meeting)
        val hasValidAgenda = normalizedAgendaItems.isNotEmpty()
        val hasValidDescription = !meeting.description.isNullOrEmpty()

        SectionHeader(title = "\u4e3b\u8981\u5185\u5bb9\u53ca\u8bae\u7a0b")
        Spacer(modifier = Modifier.height(8.dp))

        if (hasValidAgenda || hasValidDescription) {
            if (hasValidAgenda) {
                normalizedAgendaItems.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${index + 1}.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(28.dp)
                        )
                        Row(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.content,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 24.sp
                            )
                        }
                    }
                }
            } else if (hasValidDescription) {
                Text(
                    text = meeting.description!!,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 24.sp
                )
            }
        } else {
            EmptySection("\u6682\u65e0\u4e3b\u8981\u5185\u5bb9\u53ca\u8bae\u7a0b")
        }
    }
    val MaterialsSectionContent: @Composable () -> Unit = {
        SectionHeader(title = "\u4f1a\u8bae\u8d44\u6599")
        Spacer(modifier = Modifier.height(12.dp))

        if (meeting.showMediaLink) {
            val mediaLinkInteractionSource = remember { MutableInteractionSource() }
            val isMediaLinkPressed by mediaLinkInteractionSource.collectIsPressedAsState()
            val mediaLinkScale by animateFloatAsState(
                targetValue = when {
                    isMediaNavigating -> 0.985f
                    isMediaLinkPressed -> 0.992f
                    else -> 1f
                },
                animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
                label = "mediaLinkScale"
            )
            val mediaLinkAlpha by animateFloatAsState(
                targetValue = if (isMediaNavigating) 0.88f else 1f,
                animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
                label = "mediaLinkAlpha"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = mediaLinkScale
                        scaleY = mediaLinkScale
                        alpha = mediaLinkAlpha
                    }
                    .background(
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    )
                    .clickable(
                        enabled = !isMediaNavigating,
                        interactionSource = mediaLinkInteractionSource,
                        indication = null,
                        onClick = onMediaClick
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "\u5a92\u4f53\u9875\u67e5\u770b\u66f4\u591a",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "\u672c\u6b21\u4f1a\u8bae\u5305\u542b\u56fe\u7247/\u89c6\u9891\uff0c\u70b9\u51fb\u524d\u5f80\u5a92\u4f53\u9875",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.rotate(180f),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        if (!meeting.attachments.isNullOrEmpty()) {
            meeting.attachments.forEach { file ->
                val encodedName = java.net.URLEncoder.encode(file.filename, "UTF-8").replace("+", "%20")
                val fullUrl = "${staticBaseUrl}$encodedName"
                val extension = file.filename.substringAfterLast(".", "pdf")
                val uniqueName = "${fullUrl.hashCode()}.$extension"
                val localFile = File(context.cacheDir, uniqueName)

                FileItem(
                    name = file.displayName,
                    size = formatFileSize(file.fileSize),
                    localFile = if (localFile.exists()) localFile else null,
                    onClick = { onAttachmentClick(fullUrl, file.displayName) }
                )
            }
        } else {
            Text(
                text = "\u672c\u6b21\u4f1a\u8bae\u6682\u65e0\u4e0a\u4f20\u9644\u4ef6",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // Hero image participates in scrolling with parallax + fade
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
                    .clip(DetailHeroShape)
                    .graphicsLayer {
                        translationY = scrollState.value * 0.5f
                        alpha = 1f - collapseProgress
                    }
            ) {
                MeetingHeroBackground(
                    meeting = meeting,
                    accentColor = heroAccentColor,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.2f),
                                    Color.Black.copy(alpha = 0.1f),
                                    Color.Black.copy(alpha = 0.6f)
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MeetingStatusBadge(status = meeting.getUiStatus())
                        if (!meeting.meetingTypeName.isNullOrEmpty()) {
                            androidx.compose.material3.Surface(
                                color = Color.White.copy(alpha = 0.2f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = meeting.meetingTypeName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = meeting.title,
                        style = if (isPhone) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DateRange, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = formatMeetingDateTimeRange(meeting.startTime, meeting.endTime),
                                color = Color.White.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                meeting.location ?: "\u5730\u70b9\u5f85\u5b9a",
                                color = Color.White.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            // Content body sections
            Column(
                modifier = Modifier.padding(
                    start = if (isPhone) 16.dp else 24.dp,
                    end = if (isPhone) 16.dp else 24.dp,
                    top = if (isPhone) 18.dp else 22.dp
                )
            ) {
                if (isWideScreen) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(48.dp)
                    ) {
                        Column(modifier = Modifier.weight(0.3f)) {
                            InfoSectionContent()
                            Spacer(modifier = Modifier.height(32.dp))
                            AgendaSectionContent()
                        }
                        Column(modifier = Modifier.weight(0.7f)) {
                            MaterialsSectionContent()
                        }
                    }
                } else {
                    InfoSectionContent()
                    Spacer(modifier = Modifier.height(32.dp))
                    AgendaSectionContent()
                    Spacer(modifier = Modifier.height(32.dp))
                    MaterialsSectionContent()
                }
                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        // Collapsed top bar appears when hero scrolls out of view
        AnimatedVisibility(
            visible = collapseProgress > 0.85f,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                    .padding(horizontal = if (isPhone) 16.dp else 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = meeting.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 48.dp)
                )
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
    )
}

@Composable
private fun MeetingHeroBackground(
    meeting: Meeting,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        MeetingCoverImage(
            meeting = meeting,
            slot = AppImageSlot.MeetingHero,
            accentColor = accentColor,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun HeroFallbackArt(
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.95f),
                    accentColor.copy(alpha = 0.55f),
                    Color(0xFF0F172A)
                )
            )
        )
    ) {
        Surface(
            modifier = Modifier
                .size(180.dp)
                .align(Alignment.TopEnd)
                .padding(top = 20.dp, end = 20.dp),
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.08f)
        ) {}
        Surface(
            modifier = Modifier
                .size(120.dp)
                .align(Alignment.BottomStart)
                .padding(start = 32.dp, bottom = 28.dp),
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.06f)
        ) {}
    }
}

@Composable
fun FileItem(name: String, size: String, localFile: File? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), 
                androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // File Icon or Thumbnail
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                     if (localFile != null) Color.White else MaterialTheme.colorScheme.primaryContainer, 
                     androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                )
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (localFile != null) {
                PdfThumbnail(
                    filePath = localFile.absolutePath,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Menu, // Placeholder
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = size,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Read Button
        IconButton(onClick = onClick) {
             Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack, // Placeholder for "Go/Read"
                contentDescription = "Read",
                modifier = Modifier.rotate(180f),
                tint = MaterialTheme.colorScheme.primary
            )       
        }
    }
}

fun formatFileSize(size: Int): String {
    if (size < 1024) return "$size B"
    val kb = size / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    return "%.1f MB".format(mb)
}

private fun formatMeetingDateTimeRange(start: String?, end: String?): String {
    val startParsed = parseMeetingDateTime(start)
    val endParsed = parseMeetingDateTime(end)

    if (startParsed == null) {
        return start?.ifBlank { "" } ?: ""
    }

    val dateFmt = java.time.format.DateTimeFormatter.ofPattern("M\u6708d\u65e5")
    val timeFmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    val weekdayFmt = java.time.format.DateTimeFormatter.ofPattern("EEE", java.util.Locale.CHINA)

    val startDateLabel = "${startParsed.format(dateFmt)}，${startParsed.format(weekdayFmt)}"
    val startPeriod = if (startParsed.hour < 12) "\u4e0a\u5348" else "\u4e0b\u5348"
    val startTime = startParsed.format(timeFmt)

    if (endParsed == null) {
        return "$startDateLabel $startPeriod $startTime"
    }

    val sameDay = startParsed.toLocalDate() == endParsed.toLocalDate()
    return if (sameDay) {
        "$startDateLabel $startPeriod $startTime-${endParsed.format(timeFmt)}"
    } else {
        val endDateLabel = "${endParsed.format(dateFmt)}，${endParsed.format(weekdayFmt)}"
        val endPeriod = if (endParsed.hour < 12) "\u4e0a\u5348" else "\u4e0b\u5348"
        "$startDateLabel $startPeriod $startTime \u81f3 $endDateLabel $endPeriod ${endParsed.format(timeFmt)}"
    }
}
private fun parseMeetingDateTime(raw: String?): java.time.LocalDateTime? {
    if (raw.isNullOrBlank()) return null
    val normalized = raw.substringBefore(".").replace(" ", "T")
    return try {
        java.time.LocalDateTime.parse(normalized)
    } catch (_: Exception) {
        try {
            java.time.OffsetDateTime.parse(normalized).toLocalDateTime()
        } catch (_: Exception) {
            null
        }
    }
}

// Agenda Item data class
data class AgendaItem(
    val content: String = ""
)

private fun extractAgendaItems(meeting: Meeting): List<AgendaItem> {
    val fromNewField = meeting.agendaItems
        ?.mapNotNull { item ->
            item.content.takeIf { it.isNotBlank() }?.let { AgendaItem(content = it) }
        }
        .orEmpty()

    if (fromNewField.isNotEmpty()) return fromNewField
    return meeting.agenda?.let { parseAgenda(it) } ?: emptyList()
}

// Parse agenda JSON string into list of AgendaItems
fun parseAgenda(agendaJson: String): List<AgendaItem> {
    return try {
        val rawList = org.json.JSONArray(agendaJson)
        buildList {
            for (index in 0 until rawList.length()) {
                val item = rawList.optJSONObject(index)
                if (item != null) {
                    val content = item.optString("content").trim()
                    if (content.isNotEmpty()) {
                        add(AgendaItem(content = content))
                    }
                } else {
                    val content = rawList.optString(index).trim()
                    if (content.isNotEmpty()) {
                        add(AgendaItem(content = content))
                    }
                }
            }
        }
    } catch (e: Exception) {
        emptyList()
    }
}

