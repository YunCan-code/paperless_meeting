package com.example.paperlessmeeting.ui.screens.type

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.paperlessmeeting.domain.model.MeetingType
import com.example.paperlessmeeting.ui.components.GlassyTopBar

@Composable
fun MeetingTypeScreen(
    navController: NavController
) {
    Scaffold(
        topBar = { GlassyTopBar(title = "会议分类") },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            
            Text(
                text = "选择会议类型",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Text(
                text = "Browse meetings by category",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp), // Adaptive grid
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(MeetingType.values()) { type ->
                    MeetingTypeCard(
                        type = type, 
                        onClick = { navController.navigate("meeting_split/${type.name}") }
                    )
                }
            }
        }
    }
}

@Composable
fun MeetingTypeCard(
    type: MeetingType,
    onClick: () -> Unit
) {
    val icon = when(type) {
        MeetingType.Weekly -> Icons.Default.DateRange
        MeetingType.Urgent -> Icons.Default.Notifications
        MeetingType.Review -> Icons.Default.Info
        MeetingType.Kickoff -> Icons.Default.Star
        MeetingType.General -> Icons.Default.Info
    }

    // Create a rich gradient card
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.1f)
            .shadow(8.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White) // Container is white, internal box has gradient
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.brushedGradient(
                        colors = listOf(
                            type.color.copy(alpha = 0.9f),
                            type.color.copy(alpha = 0.6f)
                        )
                    )
                )
        ) {
            // Decorative Circle
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .offset(x = (-20).dp, y = (-20).dp)
                    .background(Color.White.copy(alpha = 0.2f), androidx.compose.foundation.shape.CircleShape)
            )

            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(20.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                Text(
                    text = type.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 20.sp
                )
                
                Text(
                    text = "点击查看",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

// Gradient helper
fun Brush.Companion.brushedGradient(colors: List<Color>) = linearGradient(
    colors = colors,
    start = androidx.compose.ui.geometry.Offset.Zero,
    end = androidx.compose.ui.geometry.Offset(1000f, 1000f) // Safe fallback for gradient length
)
