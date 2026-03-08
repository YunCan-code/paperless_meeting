package com.example.paperlessmeeting.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.paperlessmeeting.ui.navigation.Screen

@Composable
fun MainScreen(onLogout: () -> Unit = {}) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val tabs = listOf(
        Screen.Dashboard,
        Screen.Meetings,
        Screen.Media,
        Screen.Settings
    )

    val isReaderScreen = currentDestination?.route?.startsWith("reader") == true

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Content
            NavHost(
                navController = navController,
                startDestination = Screen.Dashboard.route,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
            ) {
                composable(Screen.Dashboard.route) {
                    com.example.paperlessmeeting.ui.screens.dashboard.DashboardScreen(
                        onMeetingClick = { meetingId ->
                            navController.navigate("meetings?meetingId=$meetingId")
                        },
                        onReadingClick = { url, name, page ->
                            val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
                            val encodedName = java.net.URLEncoder.encode(name, "UTF-8")
                            navController.navigate("reader?url=$encodedUrl&name=$encodedName&page=$page")
                        },
                        onVoteClick = {
                            navController.navigate(Screen.VoteList.route)
                        },
                        onLotteryClick = {
                            navController.navigate(Screen.LotteryList.route)
                        },
                        onCheckInClick = {
                            navController.navigate(Screen.CheckInDashboard.route)
                        }
                    )
                }

                composable(Screen.CheckInDashboard.route) {
                    com.example.paperlessmeeting.ui.screens.checkin.CheckInDashboardScreen(
                        onBackClick = { navController.popBackStack() }
                    )
                }

                composable(
                    route = Screen.Meetings.route + "?meetingId={meetingId}",
                    arguments = listOf(
                        androidx.navigation.navArgument("meetingId") {
                            type = androidx.navigation.NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { backStackEntry ->
                    val meetingId = backStackEntry.arguments?.getString("meetingId")?.toIntOrNull()
                    com.example.paperlessmeeting.ui.screens.adaptive.AdaptiveMeetingScreen(
                        meetingTypeName = "ALL",
                        navController = navController,
                        initialMeetingId = meetingId
                    )
                }
                composable(
                    "meeting_split/{typeName}",
                    arguments = listOf(androidx.navigation.navArgument("typeName") { type = androidx.navigation.NavType.StringType })
                ) { backStackEntry ->
                    val typeName = backStackEntry.arguments?.getString("typeName") ?: "ALL"
                    com.example.paperlessmeeting.ui.screens.adaptive.AdaptiveMeetingScreen(
                        meetingTypeName = typeName,
                        navController = navController
                    )
                }
                composable(Screen.Media.route) {
                    com.example.paperlessmeeting.ui.screens.media.MediaScreen()
                }
                composable(Screen.Settings.route) {
                    com.example.paperlessmeeting.ui.screens.settings.SettingsScreen(navController = navController, onLogout = onLogout)
                }

                composable(
                    route = Screen.LotteryDetail.route,
                    arguments = listOf(
                        androidx.navigation.navArgument("meetingId") { type = androidx.navigation.NavType.IntType },
                        androidx.navigation.navArgument("title") { type = androidx.navigation.NavType.StringType }
                    )
                ) { backStackEntry ->
                    val meetingId = backStackEntry.arguments?.getInt("meetingId") ?: 0
                    val title = backStackEntry.arguments?.getString("title") ?: "鎶界"
                    com.example.paperlessmeeting.ui.screens.lottery.LotteryDetailScreen(
                        meetingId = meetingId,
                        meetingTitle = title,
                        onBackClick = { navController.popBackStack() }
                    )
                }

                composable(
                    route = Screen.VoteDetail.route,
                    arguments = listOf(
                        androidx.navigation.navArgument("voteId") { type = androidx.navigation.NavType.IntType }
                    )
                ) { backStackEntry ->
                    val voteId = backStackEntry.arguments?.getInt("voteId") ?: 0
                    com.example.paperlessmeeting.ui.screens.vote.VoteDetailScreen(
                        navController = navController,
                        voteId = voteId
                    )
                }

                composable(Screen.VoteList.route) {
                    com.example.paperlessmeeting.ui.screens.vote.VoteListScreen(
                        navController = navController
                    )
                }

                composable(Screen.LotteryList.route) {
                    com.example.paperlessmeeting.ui.screens.lottery.LotteryListScreen(
                        navController = navController
                    )
                }

                composable(
                    route = "detail/{meetingId}",
                    arguments = listOf(
                        androidx.navigation.navArgument("meetingId") { type = androidx.navigation.NavType.StringType }
                    )
                ) {
                    com.example.paperlessmeeting.ui.screens.detail.DetailScreen(navController = navController)
                }

                composable(
                    route = "reader?url={url}&name={name}&page={page}",
                    arguments = listOf(
                        androidx.navigation.navArgument("url") { type = androidx.navigation.NavType.StringType },
                        androidx.navigation.navArgument("name") { type = androidx.navigation.NavType.StringType },
                        androidx.navigation.navArgument("page") {
                            type = androidx.navigation.NavType.IntType
                            defaultValue = 0
                        }
                    )
                ) { backStackEntry ->
                    val url = backStackEntry.arguments?.getString("url") ?: ""
                    val name = backStackEntry.arguments?.getString("name") ?: ""
                    val page = backStackEntry.arguments?.getInt("page") ?: 0
                    com.example.paperlessmeeting.ui.screens.reader.ReaderScreen(
                        meetingId = 0,
                        attachmentId = 0,
                        downloadUrl = url,
                        fileName = name,
                        initialPage = page,
                        navController = navController
                    )
                }
            }

            // Floating card navigation bar
            AnimatedVisibility(
                visible = !isReaderScreen,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                FloatingNavBar(
                    tabs = tabs,
                    currentRoute = currentDestination?.route?.substringBefore("?"),
                    onTabClick = { screen ->
                        if (screen == Screen.Dashboard) {
                            val popped = navController.popBackStack(Screen.Dashboard.route, inclusive = false)
                            if (!popped) {
                                navController.navigate(Screen.Dashboard.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        } else {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun FloatingNavBar(
    tabs: List<Screen>,
    currentRoute: String?,
    onTabClick: (Screen) -> Unit
) {
    val isPhone = LocalConfiguration.current.screenWidthDp < 600

    Surface(
        shape = RoundedCornerShape(50),
        shadowElevation = 8.dp,
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.padding(bottom = if (isPhone) 8.dp else 16.dp)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (isPhone) 8.dp else 12.dp,
                vertical = 4.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(if (isPhone) 4.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { screen ->
                val selected = currentRoute == screen.route
                FloatingNavItem(
                    icon = screen.icon,
                    label = screen.title,
                    selected = selected,
                    onClick = { onTabClick(screen) },
                    isPhone = isPhone
                )
            }
        }
    }
}

@Composable
private fun FloatingNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    isPhone: Boolean = false
) {
    val bgColor = if (selected)
        MaterialTheme.colorScheme.primaryContainer
    else
        Color.Transparent

    val contentColor = if (selected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(bgColor)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() }
            .padding(
                horizontal = if (isPhone) 14.dp else 18.dp,
                vertical = 6.dp
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(if (isPhone) 20.dp else 22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = if (isPhone) 11.sp else 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1
        )
    }
}

