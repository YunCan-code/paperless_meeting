package com.example.paperlessmeeting.ui.screens.detail

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.paperlessmeeting.domain.model.Meeting
import com.example.paperlessmeeting.domain.model.Vote

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailOverlayTopBar(
    currentMeeting: Meeting?,
    currentVote: Vote?,
    onVoteClick: () -> Unit,
    onCheckInClick: () -> Unit,
    onCloseClick: () -> Unit,
    isCheckInSubmitting: Boolean,
    showCheckInHint: Boolean,
    enabled: Boolean
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        TopAppBar(
            title = { },
            actions = {
                if (currentVote != null) {
                    Surface(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(40.dp),
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(enabled = enabled, onClick = onVoteClick),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Poll,
                                contentDescription = "Vote",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                if (currentMeeting?.isTodayMeeting == true) {
                    TopBarCheckInButton(
                        isCheckedIn = currentMeeting.isCheckedIn,
                        isSubmitting = isCheckInSubmitting,
                        enabled = enabled && !currentMeeting.isCheckedIn,
                        onClick = onCheckInClick,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }

                Surface(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(40.dp),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(enabled = enabled, onClick = onCloseClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )
        if (showCheckInHint && currentMeeting?.isTodayMeeting == true && !currentMeeting.isCheckedIn) {
            CheckInHintBubble(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 58.dp, end = 72.dp)
            )
        }
    }
}

@Composable
fun TopBarCheckInButton(
    isCheckedIn: Boolean,
    isSubmitting: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pulseTransition = rememberInfiniteTransition(label = "checkInPulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isCheckedIn) 1f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "checkInScale"
    )
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isCheckedIn) 1f else 0.82f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "checkInAlpha"
    )

    val backgroundColor = if (isCheckedIn) {
        Color.White.copy(alpha = 0.18f)
    } else {
        Color(0xFFE04B4B)
    }
    val borderColor = if (isCheckedIn) {
        Color.White.copy(alpha = 0.10f)
    } else {
        Color(0xFFFF8A80).copy(alpha = 0.75f)
    }

    Surface(
        modifier = modifier.graphicsLayer {
            scaleX = if (isCheckedIn || isSubmitting) 1f else pulseScale
            scaleY = if (isCheckedIn || isSubmitting) 1f else pulseScale
            alpha = if (isCheckedIn || isSubmitting) 1f else pulseAlpha
        },
        shape = RoundedCornerShape(999.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box(
            modifier = Modifier
                .clickable(enabled = enabled && !isSubmitting, onClick = onClick)
                .background(Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isCheckedIn) "已签到" else "签到",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White
            )
        }
    }
}

@Composable
fun CheckInHintBubble(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shadowElevation = 8.dp,
        tonalElevation = 2.dp
    ) {
        Text(
            text = "可点击这里签到",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}
