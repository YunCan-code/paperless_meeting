package com.example.paperlessmeeting.ui.screens.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.platform.LocalContext
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
import com.example.paperlessmeeting.BuildConfig
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.paperlessmeeting.domain.model.Meeting
import com.example.paperlessmeeting.ui.components.MeetingStatusBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    navController: NavController,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold { innerPadding ->
        // We ignore innerPadding for the top image behind status bar effect
        Box(modifier = Modifier.fillMaxSize()) {
            when (val state = uiState) {
                is DetailUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
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
                }
                is DetailUiState.Success -> {
                    MeetingDetailContent(
                        meeting = state.meeting,
                        onAttachmentClick = { url, name ->
                             val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
                             val encodedName = java.net.URLEncoder.encode(name, "UTF-8")
                             navController.navigate("reader?url=$encodedUrl&name=$encodedName")
                        }
                    )
                }
            }

            // Overlay Navigation Buttons (Back & Close)
            // Placed at the end to render ON TOP of the content/image
            TopAppBar(
                title = { },
                actions = {
                    val currentVote by viewModel.currentVote.collectAsState()
                    if (currentVote != null) {
                        // Vote Button with Glassy Style
                        Box(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(40.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(Color.Black.copy(alpha = 0.3f))
                                .clickable { viewModel.openVoteSheet() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Poll,
                                contentDescription = "Vote",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Close Button with Glassy Style
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(40.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color.Black.copy(alpha = 0.3f))
                            .clickable { navController.popBackStack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )

            // Vote Bottom Sheet
            val currentVote by viewModel.currentVote.collectAsState()
            val voteResult by viewModel.voteResult.collectAsState()
            val hasVoted by viewModel.hasVoted.collectAsState()
            val showVoteSheet by viewModel.showVoteSheet.collectAsState()
            
            if (showVoteSheet && currentVote != null) {
                com.example.paperlessmeeting.ui.components.VoteBottomSheet(
                    vote = currentVote!!,
                    hasVoted = hasVoted,
                    result = voteResult, // Pass result if available (e.g. after vote end)
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
fun MeetingDetailContent(
    meeting: Meeting,
    onAttachmentClick: (String, String) -> Unit
) {
    val context = LocalContext.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val isPhone = screenWidthDp < 600
    val isWideScreen = screenWidthDp > 700
    val heroHeight = if (isPhone) 200.dp else 280.dp
    val heroHeightPx = with(LocalDensity.current) { heroHeight.toPx() }
    val scrollState = rememberScrollState()
    val bgImage = meeting.cardImageUrl ?: "https://images.unsplash.com/photo-1542744173-8e7e53415bb0?q=80&w=2070&auto=format&fit=crop"

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
        SectionHeader(title = "与会人员")
        Spacer(modifier = Modifier.height(8.dp))

        val attendeesList = meeting.attendees?.sortedBy {
            when (it.meetingRole) {
                "主讲人" -> 0
                "特邀嘉宾" -> 1
                "参会人员" -> 2
                else -> 3
            }
        } ?: emptyList()

        if (attendeesList.isNotEmpty()) {
            val displayList = if (attendeesExpanded || attendeesList.size <= maxVisibleAttendees)
                attendeesList else attendeesList.take(maxVisibleAttendees)
            val remainingCount = attendeesList.size - maxVisibleAttendees

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                displayList.forEach { attendee ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "· ${attendee.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.width(80.dp)
                        )
                        androidx.compose.material3.Surface(
                            color = when (attendee.meetingRole) {
                                "主讲人" -> Color(0xFFFFF3E0)
                                "特邀嘉宾" -> Color(0xFFF3E5F5)
                                else -> Color(0xFFE3F2FD)
                            },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = attendee.meetingRole,
                                style = MaterialTheme.typography.labelSmall,
                                color = when (attendee.meetingRole) {
                                    "主讲人" -> Color(0xFFE65100)
                                    "特邀嘉宾" -> Color(0xFF4A148C)
                                    else -> Color(0xFF0D47A1)
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                if (!attendeesExpanded && remainingCount > 0) {
                    Text(
                        text = "...还有 $remainingCount 人，点击展开",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { attendeesExpanded = true }
                            .padding(vertical = 4.dp)
                    )
                } else if (attendeesExpanded && attendeesList.size > maxVisibleAttendees) {
                    Text(
                        text = "收起",
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
                Text("主讲人： ", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text(meeting.speaker)
            }
        } else {
            EmptySection("暂无参会人员信息")
        }
    }

    val AgendaSectionContent: @Composable () -> Unit = {
        val hasValidAgenda = !meeting.agenda.isNullOrEmpty() && meeting.agenda.trim() != "[]"
        val hasValidDescription = !meeting.description.isNullOrEmpty()

        SectionHeader(title = "会议内容 / 议程")
        Spacer(modifier = Modifier.height(8.dp))

        if (hasValidAgenda || hasValidDescription) {
            if (hasValidAgenda) {
                val agendaItems = parseAgenda(meeting.agenda!!)
                if (agendaItems.isNotEmpty()) {
                    agendaItems.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            if (item.time.isNotEmpty()) {
                                Text(
                                    text = item.time,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(60.dp)
                                )
                            }
                            Text(
                                text = item.content,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 24.sp
                            )
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
            } else if (hasValidDescription) {
                Text(
                    text = meeting.description!!,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 24.sp
                )
            }
        } else {
            EmptySection("暂无议程安排")
        }
    }

    val MaterialsSectionContent: @Composable () -> Unit = {
        SectionHeader(title = "会议资料")
        Spacer(modifier = Modifier.height(12.dp))

        if (!meeting.attachments.isNullOrEmpty()) {
            meeting.attachments.forEach { file ->
                val encodedName = java.net.URLEncoder.encode(file.filename, "UTF-8").replace("+", "%20")
                val fullUrl = "${BuildConfig.STATIC_BASE_URL}$encodedName"
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
                text = "本次会议暂无上传附件",
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
            // Hero image — participates in scrolling with parallax + fade
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
                    .graphicsLayer {
                        translationY = scrollState.value * 0.5f
                        alpha = 1f - collapseProgress
                    }
            ) {
                AsyncImage(
                    model = bgImage,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
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

                    Spacer(modifier = Modifier.height(10.dp))

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
                modifier = Modifier.padding(if (isPhone) 16.dp else 24.dp)
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

        // Collapsed top bar — appears when hero scrolls out of view
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

    val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    if (endParsed == null) {
        return startParsed.format(dateFormatter)
    }

    val sameDay = startParsed.toLocalDate() == endParsed.toLocalDate()
    return if (sameDay) {
        val startText = startParsed.format(dateFormatter)
        val endText = endParsed.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        "$startText - $endText"
    } else {
        val startText = startParsed.format(dateFormatter)
        val endText = endParsed.format(dateFormatter)
        "$startText - $endText"
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
    val time: String = "",
    val content: String = ""
)

// Parse agenda JSON string into list of AgendaItems
fun parseAgenda(agendaJson: String): List<AgendaItem> {
    return try {
        val gson = com.google.gson.Gson()
        val type = object : com.google.gson.reflect.TypeToken<List<AgendaItem>>() {}.type
        gson.fromJson(agendaJson, type) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}
