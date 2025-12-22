package com.example.paperlessmeeting.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.paperlessmeeting.domain.model.Meeting
import com.example.paperlessmeeting.domain.model.MeetingStatus

@Composable
fun MeetingCard(
    meeting: Meeting,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.98f else 1f, label = "cardScale")
    val uiType = meeting.getUiType()
    
    // Dynamic Type Logic (Matching Web HSL Algorithm)
    val typeName = meeting.meetingTypeName ?: uiType.displayName
    val typeColor = remember(typeName) {
        generateThemeColor(typeName)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp) // Fixed height for hero image look
            .padding(vertical = 8.dp)
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = {
                        onClick()
                    }
                )
            },
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Background Image
            // Priority: Backend Config > Local Fallback
            val bgImage = meeting.cardImageUrl ?: when(uiType) {
                com.example.paperlessmeeting.domain.model.MeetingType.Weekly -> "https://images.unsplash.com/photo-1431540015161-0bf868a2d407?q=80&w=2070&auto=format&fit=crop"
                com.example.paperlessmeeting.domain.model.MeetingType.Urgent -> "https://images.unsplash.com/photo-1516387938699-a93567ec168e?q=80&w=2071&auto=format&fit=crop"
                com.example.paperlessmeeting.domain.model.MeetingType.Review -> "https://images.unsplash.com/photo-1552664730-d307ca884978?q=80&w=2070&auto=format&fit=crop"
                com.example.paperlessmeeting.domain.model.MeetingType.Kickoff -> "https://images.unsplash.com/photo-1522071820081-009f0129c71c?q=80&w=2070&auto=format&fit=crop"
                else -> "https://images.unsplash.com/photo-1497366216548-37526070297c?q=80&w=2069&auto=format&fit=crop"
            }

            AsyncImage(
                model = bgImage,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // 2. Dark Gradient Overlay (for text readability)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.1f),
                                Color.Black.copy(alpha = 0.4f),
                                Color.Black.copy(alpha = 0.8f)
                            )
                        )
                    )
            )

            // 3. Content Overlay
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Row: Type Tag & Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Type Tag (Color Coded)
                    Surface(
                        color = typeColor,
                        shape = RoundedCornerShape(8.dp),
                        shadowElevation = 4.dp
                    ) {
                        Text(
                            text = typeName,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    // Status Badge (Glass-like)
                    MeetingStatusBadge(status = meeting.getUiStatus())
                }

                // Bottom Content: Title & Metada
                Column {
                    // Title
                    Text(
                        text = meeting.title,
                        style = MaterialTheme.typography.headlineSmall, // Larger
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))

                    // Metadata Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Time
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatTimeDisplay(meeting.startTime), 
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        // Location
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = meeting.location ?: "待定地点",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// Helper: Format Time
fun formatTimeDisplay(isoString: String): String {
    return try {
        if (isoString.length >= 16) {
             isoString.substring(5, 16).replace("T", " ")
        } else isoString
    } catch (e: Exception) {
        isoString
    }
}

@Composable
fun MeetingStatusBadge(status: MeetingStatus, modifier: Modifier = Modifier) {
    val (bgColor, textColor) = when (status) {
        MeetingStatus.Ongoing -> Color(0xFFE8F5E9) to Color(0xFF2E7D32)
        MeetingStatus.Upcoming -> Color(0xFFE3F2FD) to Color(0xFF1565C0)
        MeetingStatus.Finished -> Color(0xFFEEEEEE) to Color(0xFF757575)
        MeetingStatus.Draft -> Color(0xFFFFF3E0) to Color(0xFFEF6C00)
    }

    // Use a lighter/glassy look for the dark background
    Surface(
        color = Color.White.copy(alpha = 0.9f), // Always white-ish background for contrast on image
        contentColor = textColor, // Text color matches status
        shape = CircleShape,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            if (status == MeetingStatus.Ongoing) {
                Canvas(modifier = Modifier.size(6.dp)) {
                    drawCircle(color = textColor)
                }
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = status.displayName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = textColor // Explicitly set text color
            )
        }
    }
}

// Helper: HSL Color Generator (Matches Web Logic)
fun generateThemeColor(str: String?): Color {
    if (str.isNullOrEmpty()) return Color(0xFF3B82F6) // default blue
    var hash = 0
    str.forEach { char ->
        hash = char.code + ((hash shl 5) - hash)
    }
    
    val h = kotlin.math.abs(hash) % 360
    val s = 60 + (kotlin.math.abs(hash) % 20) // 60-80%
    val l = 45 + (kotlin.math.abs(hash) % 15) // 45-60%
    
    return hslToColor(h.toFloat(), s.toFloat(), l.toFloat())
}

fun hslToColor(h: Float, s: Float, l: Float): Color {
    val sNorm = s / 100f
    val lNorm = l / 100f
    
    val c = (1 - kotlin.math.abs(2 * lNorm - 1)) * sNorm
    val x = c * (1 - kotlin.math.abs((h / 60) % 2 - 1))
    val m = lNorm - c / 2

    val (r, g, b) = when {
        h < 60 -> Triple(c, x, 0f)
        h < 120 -> Triple(x, c, 0f)
        h < 180 -> Triple(0f, c, x)
        h < 240 -> Triple(0f, x, c)
        h < 300 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x) // h < 360
    }
    
    return Color(r + m, g + m, b + m, 1f)
}
