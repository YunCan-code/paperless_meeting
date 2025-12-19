package com.example.paperlessmeeting.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White // Assume dark overlay
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        // We ignore innerPadding for the top image behind status bar effect
        Box(modifier = Modifier.fillMaxSize()) {
            when (val state = uiState) {
                is DetailUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is DetailUiState.Error -> {
                    Text("Error: ${state.message}", modifier = Modifier.align(Alignment.Center))
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
        }
    }
}

@Composable
fun MeetingDetailContent(
    meeting: Meeting,
    onAttachmentClick: (String, String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Hero Image Area
        // Fallback logic similar to MeetingCard if url missing (should stick to backend though)
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
                            colors = listOf(Color.Black.copy(alpha = 0.3f), Color.Black.copy(alpha = 0.8f))
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(24.dp)
            ) {
                // Status + Type Row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MeetingStatusBadge(status = meeting.getUiStatus())
                    Spacer(modifier = Modifier.width(8.dp))
                    if (!meeting.meetingTypeName.isNullOrEmpty()) {
                        androidx.compose.material3.Surface(
                           color = Color.White.copy(alpha = 0.2f),
                           shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                        ) {
                             Text(
                                 text = meeting.meetingTypeName,
                                 style = MaterialTheme.typography.labelSmall,
                                 color = Color.White,
                                 modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                             )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = meeting.title,
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Location & Host
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Menu, contentDescription=null, tint=Color.White.copy(alpha=0.8f), modifier=Modifier.size(16.dp)) 
                    Text(
                        text = meeting.location ?: "地点待定",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Content Body
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(scrollState)
        ) {
            // 1. Description / Agenda
            SectionHeader(title = "会议内容 / 议程")
            Spacer(modifier = Modifier.height(8.dp))
            if (!meeting.description.isNullOrEmpty()) {
                Text(
                    text = meeting.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 24.sp
                )
            } else {
                Text(
                    text = "暂无详细描述",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 2. Personnel (Host)
            SectionHeader(title = "参会信息")
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("主持人： ", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text(meeting.host ?: "未指定")
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 3. Attachments
            SectionHeader(title = "会议资料")
            Spacer(modifier = Modifier.height(12.dp))
            
            if (!meeting.attachments.isNullOrEmpty()) {
                meeting.attachments.forEach { file ->
                    val fullUrl = "http://10.0.2.2:8000/static/${file.filename}"
                    FileItem(
                        name = file.displayName, 
                        size = formatFileSize(file.fileSize),
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
            
            // Bottom Spacer for scrolling
            Spacer(modifier = Modifier.height(80.dp))
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
fun FileItem(name: String, size: String, onClick: () -> Unit) {
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
        // File Icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Menu, // Placeholder
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
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
                imageVector = Icons.AutoMirrored.Filled.ArrowBack, // Placeholder for "Go/Read" (rotated 180?)
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
