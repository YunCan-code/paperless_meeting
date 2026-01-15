package com.example.paperlessmeeting.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
                        IconButton(onClick = { viewModel.openVoteSheet() }) {
                            Icon(
                                imageVector = Icons.Default.Poll, // Use Poll or HowToVote
                                contentDescription = "Vote",
                                tint = Color.White
                            )
                        }
                    }

                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
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
    Column(modifier = Modifier.fillMaxSize()) {
        // Hero Image Area
        val bgImage = meeting.cardImageUrl ?: "https://images.unsplash.com/photo-1542744173-8e7e53415bb0?q=80&w=2070&auto=format&fit=crop"
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
        ) {
            AsyncImage(
                model = bgImage,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // Gradient Overlay for Text Visibility
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

            // TOP LEFT: Status + Type Badges
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(20.dp),
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

            // BOTTOM: Title + Location + Time
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Title
                Text(
                    text = meeting.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Location & Time Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Location
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = meeting.location ?: "地点待定",
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    // Time
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.DateRange,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = try {
                                java.time.LocalDateTime.parse(meeting.startTime.substringBefore("."))
                                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                            } catch (e: Exception) {
                                meeting.startTime.substringBefore("T")
                            },
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // Content Body
        // Content Body
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val isWideScreen = maxWidth > 700.dp
            
            // Define sections as local composables to reuse logic
            val InfoSectionContent: @Composable () -> Unit = {
                SectionHeader(title = "参会信息")
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("主讲人： ", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text(meeting.speaker ?: "未指定")
                }
            }
            
            val AgendaSectionContent: @Composable () -> Unit = {
                 val hasValidAgenda = !meeting.agenda.isNullOrEmpty() && meeting.agenda.trim() != "[]"
                 val hasValidDescription = !meeting.description.isNullOrEmpty()
                 
                 if (hasValidAgenda || hasValidDescription) {
                    SectionHeader(title = "会议内容 / 议程")
                    Spacer(modifier = Modifier.height(8.dp))
                    
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
                 }
            }
            
            val MaterialsSectionContent: @Composable () -> Unit = {
                SectionHeader(title = "会议资料")
                Spacer(modifier = Modifier.height(12.dp))
                
                if (!meeting.attachments.isNullOrEmpty()) {
                    meeting.attachments.forEach { file ->
                        val encodedName = java.net.URLEncoder.encode(file.filename, "UTF-8").replace("+", "%20")
                        val fullUrl = "https://coso.top/static/$encodedName"
                        
                        // Check for local cached file using exact logic from ReaderViewModel
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

            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(scrollState)
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
