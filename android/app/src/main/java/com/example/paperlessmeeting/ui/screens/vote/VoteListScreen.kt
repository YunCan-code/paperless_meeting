package com.example.paperlessmeeting.ui.screens.vote

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.paperlessmeeting.domain.model.Vote

// Minimalist Color Palette
private val ActiveBlue = Color(0xFF3B82F6) // Softer blue
private val ClosedGray = Color(0xFF64748B) // Slate gray for closed
private val DraftGray = Color(0xFF9CA3AF)
private val LightBackground = Color(0xFFF9FAFB) // Very light gray background
private val SurfaceWhite = Color(0xFFFFFFFF)
private val TextPrimary = Color(0xFF111827)
private val TextSecondary = Color(0xFF6B7280)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoteListScreen(
    navController: NavController,
    viewModel: VoteListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        "投票中心", 
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = SurfaceWhite
                ),
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = TextPrimary)
                    }
                }
            )
        },
        containerColor = LightBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Minimalist Tabs
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = SurfaceWhite,
                contentColor = ActiveBlue,
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = ActiveBlue,
                        height = 3.dp // Slightly thicker
                    )
                },
                divider = { Divider(color = Color.Transparent) } // No divider
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { 
                        Text(
                            "进行中 (${uiState.activeVotes.size})",
                            fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal
                        ) 
                    },
                    selectedContentColor = ActiveBlue,
                    unselectedContentColor = TextSecondary
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { 
                        Text(
                            "历史记录",
                            fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal
                        ) 
                    },
                    selectedContentColor = ActiveBlue,
                    unselectedContentColor = TextSecondary
                )
            }

            Spacer(Modifier.height(16.dp))

            // Animated Content for switching lists
            AnimatedContent(
                targetState = selectedTabIndex,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith 
                    fadeOut(animationSpec = tween(300))
                },
                label = "TabTransition"
            ) { targetIndex ->
                 // Content
                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ActiveBlue)
                    }
                } else if (uiState.error != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Error: ${uiState.error}",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    val currentVotes = if (targetIndex == 0) uiState.activeVotes else uiState.historyVotes
                    
                    if (currentVotes.isEmpty()) {
                        EmptyStateView()
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(20.dp), // Increased spacing
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(currentVotes) { vote ->
                                MinimalistVoteCard(
                                    vote = vote,
                                    onClick = { 
                                        navController.navigate("vote_detail/${vote.id}")
                                    }
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
private fun MinimalistVoteCard(
    vote: Vote,
    onClick: () -> Unit
) {
    val isActive = vote.status == "active"
    val barColor = when (vote.status) {
        "active" -> ActiveBlue
        "closed" -> ClosedGray
        else -> DraftGray
    }

    // Pulsing animation for active votes
    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if(isActive) 8.dp else 2.dp, // Deeper shadow for active
                shape = RoundedCornerShape(12.dp),
                spotColor = if(isActive) barColor.copy(alpha = 0.2f) else Color.Black.copy(alpha=0.05f)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(color = barColor),
                onClick = onClick
            ),
        shape = RoundedCornerShape(12.dp),
        color = SurfaceWhite
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Left Indicator Bar
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(barColor) // Full color, no alpha for closed
            )

            // Content Area
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .weight(1f)
            ) {
                // Header: Status Badge (Minimal Text)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isActive) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(barColor.copy(alpha = if(isActive) 1f else 1f)) // Solid dot
                        )
                        // Outer pulse ring
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .offset(x = (-12).dp) // Centered over dot
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(barColor.copy(alpha = pulseAlpha))
                        )
                        Spacer(Modifier.width(0.dp)) // Offset covered spacing
                        Text(
                            text = "进行中",
                            style = MaterialTheme.typography.labelSmall,
                            color = barColor,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = if (vote.status == "closed") "已结束" else "草稿",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Spacer(Modifier.weight(1f))
                    
                    Text(
                        text = vote.created_at.take(10),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Title
                Text(
                    text = vote.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        letterSpacing = 0.5.sp
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                vote.description?.let { desc ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 2
                    )
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Footer: Meta info or Action text
                Row(verticalAlignment = Alignment.CenterVertically) {
                     // Type tag
                     Surface(
                         color = LightBackground,
                         shape = RoundedCornerShape(6.dp)
                     ) {
                         Text(
                             text = if (vote.is_multiple) "多选" else "单选",
                             modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                             style = MaterialTheme.typography.labelSmall,
                             color = TextSecondary
                         )
                     }
                     
                     if (vote.is_anonymous) {
                         Spacer(Modifier.width(8.dp))
                         Surface(
                             color = LightBackground,
                             shape = RoundedCornerShape(6.dp)
                         ) {
                             Text(
                                 text = "匿名",
                                 modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                 style = MaterialTheme.typography.labelSmall,
                                 color = TextSecondary
                             )
                         }
                     }
                }
            }
        }
    }
}

@Composable
fun EmptyStateView() {
    Box(
        modifier = Modifier.fillMaxSize(), 
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.HowToVote, 
                contentDescription = null,
                tint = Color.LightGray.copy(alpha = 0.5f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "暂无投票记录", 
                color = TextSecondary.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
