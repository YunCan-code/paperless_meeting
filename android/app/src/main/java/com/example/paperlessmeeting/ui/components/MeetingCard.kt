package com.example.paperlessmeeting.ui.components

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.example.paperlessmeeting.domain.model.Meeting
import com.example.paperlessmeeting.domain.model.MeetingStatus
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun MeetingCard(
    meeting: Meeting,
    onClick: () -> Unit,
    statusOverride: MeetingStatus? = null,
    placeLocationBottomEnd: Boolean = false,
    showLocation: Boolean = true
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
        shape = RoundedCornerShape(20.dp), // 改为 20.dp
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Background Image
            // Priority: Backend Config > Local Fallback
            val bgImage = meeting.cardImageThumbUrl ?: meeting.cardImageUrl ?: when(uiType) {
                com.example.paperlessmeeting.domain.model.MeetingType.Weekly -> "https://images.unsplash.com/photo-1431540015161-0bf868a2d407?q=80&w=2070&auto=format&fit=crop"
                com.example.paperlessmeeting.domain.model.MeetingType.Urgent -> "https://images.unsplash.com/photo-1516387938699-a93567ec168e?q=80&w=2071&auto=format&fit=crop"
                com.example.paperlessmeeting.domain.model.MeetingType.Review -> "https://images.unsplash.com/photo-1552664730-d307ca884978?q=80&w=2070&auto=format&fit=crop"
                com.example.paperlessmeeting.domain.model.MeetingType.Kickoff -> "https://images.unsplash.com/photo-1522071820081-009f0129c71c?q=80&w=2070&auto=format&fit=crop"
                else -> "https://images.unsplash.com/photo-1497366216548-37526070297c?q=80&w=2069&auto=format&fit=crop"
            }
            val context = LocalContext.current
            val optimizedBgImage = remember(bgImage) { optimizeMeetingCardImageUrl(bgImage) }
            val imageRequest = remember(context, optimizedBgImage) {
                ImageRequest.Builder(context)
                    .data(optimizedBgImage)
                    .crossfade(false)
                    .allowHardware(true)
                    .precision(Precision.INEXACT)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .build()
            }

            val painter = rememberAsyncImagePainter(model = imageRequest)
            val painterState = painter.state

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                typeColor.copy(alpha = 0.96f),
                                typeColor.copy(alpha = 0.65f),
                                Color(0xFF0F172A)
                            )
                        )
                    )
            ) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 16.dp, end = 16.dp)
                        .size(88.dp),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.07f)
                ) {}
            }

            if (painterState is AsyncImagePainter.State.Success) {
                Image(
                    painter = painter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

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
                    .padding(20.dp)
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
                        shadowElevation = 2.dp // 弱化阴影
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
                    MeetingStatusBadge(status = statusOverride ?: meeting.getUiStatus())
                }

                // Title (中间偏上)
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = meeting.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Spacer 撑开，将 Metadata 推到底部
                Spacer(modifier = Modifier.weight(1f))

                // Metadata
                val speakersList = meeting.attendees?.filter { it.meetingRole == "主讲人" }?.joinToString(", ") { it.name }
                val displaySpeaker = if (!speakersList.isNullOrBlank()) speakersList else meeting.speaker

                if (placeLocationBottomEnd) {
                    Column {
                        if (!displaySpeaker.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = displaySpeaker,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.9f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Time (left bottom)
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = formatTimeRange(meeting.startTime, meeting.endTime),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }

                            if (showLocation) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Location (right bottom)
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
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!displaySpeaker.isNullOrBlank()) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = displaySpeaker,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.9f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                        }

                        if (showLocation) {
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
                            Spacer(modifier = Modifier.width(16.dp))
                        }

                        // Time
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatTimeRange(meeting.startTime, meeting.endTime),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }
    }
}

// Helper: Parse ISO datetime string to LocalDateTime
private fun parseDateTime(isoString: String): LocalDateTime? {
    return try {
        val normalized = isoString.substringBefore(".").replace(" ", "T")
        LocalDateTime.parse(normalized)
    } catch (_: Exception) {
        null
    }
}

// Helper: Format Time Range
fun formatTimeRange(start: String, end: String?): String {
    val startDt = parseDateTime(start)
    val endDt = end?.takeIf { it.isNotBlank() }?.let { parseDateTime(it) }

    if (startDt == null) return start

    val dateFmt = DateTimeFormatter.ofPattern("M月d日")
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    val weekdayFmt = DateTimeFormatter.ofPattern("EEE", java.util.Locale.CHINA)
    val startLabel = "${startDt.format(dateFmt)}（${startDt.format(weekdayFmt)}）"
    val startPeriod = if (startDt.hour < 12) "上午" else "下午"
    val startTime = startDt.format(timeFmt)

    if (endDt == null) return "$startLabel $startPeriod $startTime"

    return if (startDt.toLocalDate() == endDt.toLocalDate()) {
        "$startLabel $startPeriod $startTime-${endDt.format(timeFmt)}"
    } else {
        val endLabel = "${endDt.format(dateFmt)}（${endDt.format(weekdayFmt)}）"
        val endPeriod = if (endDt.hour < 12) "上午" else "下午"
        "$startLabel $startPeriod $startTime 至 $endLabel $endPeriod ${endDt.format(timeFmt)}"
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

private const val CARD_IMAGE_WIDTH = 960
private const val CARD_IMAGE_HEIGHT = 540
private const val CARD_IMAGE_QUALITY = 70

private fun optimizeMeetingCardImageUrl(url: String): String {
    return try {
        val uri = Uri.parse(url)
        val host = uri.host ?: return url
        if (!host.contains("images.unsplash.com", ignoreCase = true)) return url

        val builder = uri.buildUpon().clearQuery()
        uri.queryParameterNames
            .filterNot { key ->
                key.equals("w", ignoreCase = true) ||
                key.equals("h", ignoreCase = true) ||
                key.equals("q", ignoreCase = true) ||
                key.equals("fit", ignoreCase = true) ||
                key.equals("auto", ignoreCase = true)
            }
            .forEach { key ->
                uri.getQueryParameters(key).forEach { value ->
                    builder.appendQueryParameter(key, value)
                }
            }

        builder
            .appendQueryParameter("auto", "format")
            .appendQueryParameter("fit", "crop")
            .appendQueryParameter("w", CARD_IMAGE_WIDTH.toString())
            .appendQueryParameter("h", CARD_IMAGE_HEIGHT.toString())
            .appendQueryParameter("q", CARD_IMAGE_QUALITY.toString())
            .build()
            .toString()
    } catch (e: Exception) {
        url
    }
}
