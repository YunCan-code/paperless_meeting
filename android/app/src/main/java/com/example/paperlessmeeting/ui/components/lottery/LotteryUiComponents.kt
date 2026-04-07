package com.example.paperlessmeeting.ui.components.lottery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.platform.LocalDensity
import com.example.paperlessmeeting.ui.theme.CardBackground
import com.example.paperlessmeeting.ui.theme.PrimaryBlue
import com.example.paperlessmeeting.ui.theme.TextPrimary
import com.example.paperlessmeeting.ui.theme.TextSecondary
import androidx.compose.foundation.layout.ExperimentalLayoutApi

enum class LotteryChipTone {
    Neutral,
    Primary,
    Success,
    Warning,
    Danger
}

private fun resolveLotteryToneColors(tone: LotteryChipTone): Pair<Color, Color> {
    return when (tone) {
        LotteryChipTone.Primary -> Color(0xFFDCEBFF) to Color(0xFF1D6FD6)
        LotteryChipTone.Success -> Color(0xFFDDF7E7) to Color(0xFF166534)
        LotteryChipTone.Warning -> Color(0xFFFFE9C7) to Color(0xFFB45309)
        LotteryChipTone.Danger -> Color(0xFFFEE2E2) to Color(0xFFB91C1C)
        LotteryChipTone.Neutral -> Color(0xFFF1F5F9) to Color(0xFF475569)
    }
}

val LotteryCardBorderColor = Color(0xFFDCE4F0)
private val LotteryMutedCardColor = Color(0xFFF8FBFF)
private val LotteryStageBackground = Brush.verticalGradient(
    colors = listOf(Color(0xFFF7FBFF), Color(0xFFEAF3FF))
)
private val LotteryStageHighlight = Color(0xFFEDF5FF)

@Composable
fun LotteryStatusPill(
    text: String,
    tone: LotteryChipTone,
    modifier: Modifier = Modifier
) {
    val (container, content) = resolveLotteryToneColors(tone)
    Surface(
        color = container,
        shape = RoundedCornerShape(999.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            color = content,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun LotteryMetaChip(
    text: String,
    tone: LotteryChipTone = LotteryChipTone.Neutral,
    leadingIcon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    val (container, content) = resolveLotteryToneColors(tone)
    Surface(
        color = container,
        shape = RoundedCornerShape(999.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            leadingIcon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

            Text(
                text = text,
                color = content,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun LotterySectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = TextPrimary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        subtitle?.takeIf { it.isNotBlank() }?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = it,
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 21.sp
            )
        }
    }
}

@Composable
fun LotteryCardFrame(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = CardBackground,
        border = BorderStroke(1.dp, LotteryCardBorderColor),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
fun LotterySupportInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = TextPrimary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun LotteryActionBar(
    message: String,
    primaryLabel: String? = null,
    primaryEnabled: Boolean = false,
    secondaryLabel: String? = null,
    secondaryEnabled: Boolean = false,
    actionInProgress: Boolean = false,
    onPrimaryClick: () -> Unit = {},
    onSecondaryClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (primaryLabel != null || secondaryLabel != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    primaryLabel?.let { label ->
                        Button(
                            onClick = onPrimaryClick,
                            enabled = primaryEnabled && !actionInProgress,
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            modifier = Modifier.sizeIn(minHeight = 40.dp)
                        ) {
                            Text(
                                text = if (actionInProgress) "处理中..." else label,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    secondaryLabel?.let { label ->
                        OutlinedButton(
                            onClick = onSecondaryClick,
                            enabled = secondaryEnabled && !actionInProgress,
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                            border = BorderStroke(1.dp, Color(0xFFF4A3A3)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB42318)),
                            modifier = Modifier.sizeIn(minHeight = 40.dp)
                        ) {
                            Text(
                                text = if (actionInProgress) "处理中..." else label,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Text(
                text = message,
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 20.sp,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
fun LotteryInlineEmptyState(
    icon: ImageVector,
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .background(PrimaryBlue.copy(alpha = 0.08f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(30.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = title,
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        description?.takeIf { it.isNotBlank() }?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = it,
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun LotterySlotMachineStage(
    names: List<String>,
    modifier: Modifier = Modifier,
    minHeight: Dp = 280.dp
) {
    val columnCount = 3
    val itemHeight = 72.dp
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }
    val stageNames = if (names.isEmpty()) listOf("待开始") else names
    val columns = List(columnCount) { columnIndex ->
        val rotated = stageNames.drop(columnIndex) + stageNames.take(columnIndex)
        if (rotated.isEmpty()) stageNames else rotated
    }
    val infiniteTransition = rememberInfiniteTransition(label = "lottery-slot-stage")
    val progresses = List(columnCount) { index ->
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 1500 + index * 220,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Restart,
                initialStartOffset = StartOffset(
                    offsetMillis = index * 180,
                    offsetType = StartOffsetType.FastForward
                )
            ),
            label = "slot-progress-$index"
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(RoundedCornerShape(28.dp))
            .background(LotteryStageBackground)
            .border(1.dp, Color(0xFFD7E6FA), RoundedCornerShape(28.dp))
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFFFFFFF), Color(0x00FFFFFF), Color(0xFFFFFFFF)),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(itemHeight)
                .clip(RoundedCornerShape(22.dp))
                .background(LotteryStageHighlight)
                .border(1.dp, Color(0xFFCEE0FB), RoundedCornerShape(22.dp))
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(76.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFF7FBFF), Color(0x00F7FBFF))
                    )
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(76.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0x00EAF3FF), Color(0xFFEAF3FF))
                    )
                )
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            columns.forEachIndexed { index, baseNames ->
                val trackNames = baseNames + baseNames
                val shift = itemHeightPx * baseNames.size

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                translationY = -(shift * progresses[index].value)
                            },
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        trackNames.forEach { name ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(itemHeight),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = name,
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LotteryStagePlaceholder(
    roundLabel: String?,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    minHeight: Dp = 280.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFFF8FBFF))
            .border(1.dp, Color(0xFFDCE6F5), RoundedCornerShape(28.dp))
            .padding(horizontal = 22.dp, vertical = 20.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            roundLabel?.takeIf { it.isNotBlank() }?.let {
                LotteryMetaChip(text = it, tone = LotteryChipTone.Primary)
            }
            Text(
                text = title,
                color = TextPrimary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = description,
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LotteryWinnerStage(
    roundLabel: String?,
    title: String,
    winnerNames: List<String>,
    modifier: Modifier = Modifier,
    minHeight: Dp = 280.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFFF8FBFF))
            .border(1.dp, Color(0xFFDCE6F5), RoundedCornerShape(28.dp))
            .padding(horizontal = 22.dp, vertical = 20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            roundLabel?.takeIf { it.isNotBlank() }?.let {
                LotteryMetaChip(text = it, tone = LotteryChipTone.Warning)
                Spacer(modifier = Modifier.height(12.dp))
            }
            Text(
                text = title,
                color = TextPrimary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    winnerNames.forEach { name ->
                        LotteryMetaChip(text = name, tone = LotteryChipTone.Primary)
                    }
                }
            }
        }
    }
}

@Composable
fun LotteryEmptyStateCard(
    icon: ImageVector,
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = CardBackground,
        border = BorderStroke(1.dp, LotteryCardBorderColor),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 36.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .background(PrimaryBlue.copy(alpha = 0.08f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = title,
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            description?.takeIf { it.isNotBlank() }?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
fun LotteryInfoChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFF4F7FB),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = value,
                color = TextPrimary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun LotteryRoundRecordCard(
    title: String,
    statusLabel: String,
    statusTone: LotteryChipTone,
    metaText: String,
    winnerSummary: String?,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false
) {
    val background = CardBackground
    val border = if (highlighted) Color(0xFFD6E5FF) else LotteryCardBorderColor

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = background,
        border = BorderStroke(1.dp, border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .background(background)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = metaText,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                LotteryStatusPill(text = statusLabel, tone = statusTone)
            }

            winnerSummary?.takeIf { it.isNotBlank() }?.let {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = border.copy(alpha = 0.35f)
                ) {
                    Text(
                        text = it,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}
