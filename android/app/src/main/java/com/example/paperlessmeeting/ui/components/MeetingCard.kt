package com.example.paperlessmeeting.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.paperlessmeeting.domain.model.Meeting
import com.example.paperlessmeeting.domain.model.MeetingStatus

@Composable
fun MeetingCard(
    meeting: Meeting,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.96f else 1f, label = "cardScale")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                        onClick()
                    }
                )
            },
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        )
    ) {
        Column {
            // Header Image (Optional)
            if (meeting.imageUrl != null) {
                Box(modifier = Modifier.height(140.dp)) {
                    AsyncImage(
                        model = meeting.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Gradient overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.6f)
                                    )
                                )
                            )
                    )
                    // Status Badge over Image
                    MeetingStatusBadge(
                        status = meeting.status,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .padding(20.dp)
            ) {
                // Type Tag & Status (if no image)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Type Tag with Dynamic Color
                    Surface(
                        color = meeting.type.color.copy(alpha = 0.15f),
                        contentColor = meeting.type.color,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = meeting.type.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (meeting.imageUrl == null) {
                        MeetingStatusBadge(status = meeting.status)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Title
                Text(
                    text = meeting.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Info Rows
                InfoRow(icon = Icons.Filled.DateRange, text = "${meeting.startTime} - ${meeting.endTime}")
                Spacer(modifier = Modifier.height(6.dp))
                InfoRow(icon = Icons.Filled.LocationOn, text = meeting.location)
                Spacer(modifier = Modifier.height(6.dp))
                InfoRow(icon = Icons.Outlined.Person, text = "主持人: ${meeting.host}")
            }
        }
    }
}

@Composable
fun MeetingStatusBadge(status: MeetingStatus, modifier: Modifier = Modifier) {
    val (bgColor, textColor) = when (status) {
        MeetingStatus.Ongoing -> Color(0xFFE8F5E9) to Color(0xFF2E7D32) // Light Green
        MeetingStatus.Upcoming -> Color(0xFFE3F2FD) to Color(0xFF1565C0) // Light Blue
        MeetingStatus.Finished -> Color(0xFFEEEEEE) to Color(0xFF757575) // Grey
        MeetingStatus.Draft -> Color(0xFFFFF3E0) to Color(0xFFEF6C00)   // Orange
    }

    Surface(
        color = bgColor,
        contentColor = textColor,
        shape = CircleShape,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            if (status == MeetingStatus.Ongoing) {
                PulsingDot(color = textColor)
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = status.displayName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun InfoRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun PulsingDot(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
//    val alpha by infiniteTransition.animateFloat(
//        initialValue = 0.2f,
//        targetValue = 1f,
//        animationSpec = infiniteRepeatable(
//            animation = tween(1000),
//            repeatMode = RepeatMode.Reverse
//        ),
//        label = "alpha"
//    )
    // Simplified static for now to avoid complexity imports without preview
    Canvas(modifier = Modifier.size(8.dp)) {
        drawCircle(color = color)
    }
}
