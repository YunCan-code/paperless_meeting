package com.example.paperlessmeeting.ui.components.vote

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.paperlessmeeting.ui.theme.BackgroundLayer
import com.example.paperlessmeeting.ui.theme.CardBackground
import com.example.paperlessmeeting.ui.theme.PrimaryBlue
import com.example.paperlessmeeting.ui.theme.SuccessGreen
import com.example.paperlessmeeting.ui.theme.TextPrimary
import com.example.paperlessmeeting.ui.theme.TextSecondary

data class VoteStatusVisual(
    val label: String,
    val containerColor: Color,
    val contentColor: Color
)

enum class VoteChipTone {
    Neutral,
    Primary,
    Success,
    Warning,
    Danger
}

fun resolveVoteStatusVisual(
    status: String,
    hasVoted: Boolean = false,
    waitLeft: Int = 0
): VoteStatusVisual {
    return when {
        status == "closed" -> VoteStatusVisual(
            label = "已结束",
            containerColor = Color(0xFFE2E8F0),
            contentColor = Color(0xFF475569)
        )
        waitLeft > 0 -> VoteStatusVisual(
            label = "即将开始",
            containerColor = Color(0xFFFFF1D6),
            contentColor = Color(0xFFB45309)
        )
        hasVoted -> VoteStatusVisual(
            label = "已参与",
            containerColor = SuccessGreen.copy(alpha = 0.12f),
            contentColor = Color(0xFF166534)
        )
        else -> VoteStatusVisual(
            label = "进行中",
            containerColor = PrimaryBlue.copy(alpha = 0.12f),
            contentColor = PrimaryBlue
        )
    }
}

@Composable
fun VoteStatusPill(
    visual: VoteStatusVisual,
    modifier: Modifier = Modifier
) {
    Surface(
        color = visual.containerColor,
        shape = RoundedCornerShape(999.dp),
        modifier = modifier
    ) {
        Text(
            text = visual.label,
            color = visual.contentColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun VoteMetaChip(
    text: String,
    tone: VoteChipTone = VoteChipTone.Neutral,
    leadingIcon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    val (container, content) = when (tone) {
        VoteChipTone.Primary -> PrimaryBlue.copy(alpha = 0.1f) to PrimaryBlue
        VoteChipTone.Success -> SuccessGreen.copy(alpha = 0.12f) to Color(0xFF166534)
        VoteChipTone.Warning -> Color(0xFFFFF4E5) to Color(0xFFB45309)
        VoteChipTone.Danger -> Color(0xFFFEE2E2) to Color(0xFFB91C1C)
        VoteChipTone.Neutral -> BackgroundLayer to TextSecondary
    }

    Surface(
        color = container,
        shape = RoundedCornerShape(999.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
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
fun VoteSegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CardBackground,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = if (selected) PrimaryBlue.copy(alpha = 0.12f) else Color.Transparent,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onSelect(index) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (selected) PrimaryBlue else TextSecondary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun VoteEmptyStateCard(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = CardBackground,
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

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}
