package com.example.paperlessmeeting.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.paperlessmeeting.ui.navigation.Screen
import com.example.paperlessmeeting.ui.screens.adaptive.AdaptiveMeetingScreen
import com.example.paperlessmeeting.ui.screens.dashboard.DashboardScreen // Will create this next
import com.example.paperlessmeeting.ui.theme.PaperlessMeetingTheme

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val items = listOf(
        Screen.Dashboard,
        Screen.Meetings,
        Screen.Settings
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background // #F5F5F7 ideal, set in Theme later
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // === Navigation Rail (Left) ===
            // Hide rail when in Reader mode
            val isReaderScreen = currentDestination?.route?.startsWith("reader") == true
            
            AnimatedVisibility(
                visible = !isReaderScreen,
                enter = slideInHorizontally() + fadeIn(),
                exit = slideOutHorizontally() + fadeOut()
            ) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    windowInsets = WindowInsets.safeDrawing // Ensure rail respects system bars/cutouts
                ) {
                    // Top items (Dashboard, Meetings)
                    items.filter { it != Screen.Settings }.forEach { screen ->
                        NavigationRailItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentDestination?.route?.substringBefore("?") == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                }
                            }
                        )
                    }

                    // Push Settings to bottom
                    Spacer(modifier = Modifier.weight(1f))

                    // Bottom item (Settings)
                    val settingsSchema = Screen.Settings
                    NavigationRailItem(
                        icon = { Icon(settingsSchema.icon, contentDescription = settingsSchema.title) },
                        label = { Text(settingsSchema.title) },
                        selected = currentDestination?.route == settingsSchema.route,
                        onClick = {
                            navController.navigate(settingsSchema.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp)) // Bottom padding
                }
            }

            // === Main Content Area (Right) ===
            NavHost(
                navController = navController,
                startDestination = Screen.Dashboard.route,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing) // Ensure content is safe from cutouts/bars
            ) {
                composable(Screen.Dashboard.route) {
                    // Placeholder for Dashboard
                    com.example.paperlessmeeting.ui.screens.dashboard.DashboardScreen(
                        onMeetingClick = { meetingId ->
                            navController.navigate("meetings?meetingId=$meetingId")
                        },
                        onReadingClick = { url, name, page ->
                            val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
                            val encodedName = java.net.URLEncoder.encode(name, "UTF-8")
                            val encodedname = java.net.URLEncoder.encode(name, "UTF-8")
                            navController.navigate("reader?url=$encodedUrl&name=$encodedname&page=$page")
                        },
                        onVoteClick = {
                            navController.navigate(Screen.VoteList.route)
                        },
                        onLotteryClick = {
                            navController.navigate(Screen.LotteryDetail.createRoute(it.id, it.title))
                        }
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
                    // Default to "ALL" + meetingId selection
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
                composable(Screen.Settings.route) {
                    com.example.paperlessmeeting.ui.screens.settings.SettingsScreen(navController = navController)
                }

                composable(
                    route = Screen.LotteryDetail.route,
                    arguments = listOf(
                        androidx.navigation.navArgument("meetingId") { type = androidx.navigation.NavType.IntType },
                        androidx.navigation.navArgument("title") { type = androidx.navigation.NavType.StringType }
                    )
                ) { backStackEntry ->
                    val meetingId = backStackEntry.arguments?.getInt("meetingId") ?: 0
                    val title = backStackEntry.arguments?.getString("title") ?: "抽签"
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
                
                // Keep Detail route accessible if needed for phone view deep links context
                // But in Rail view, Detail creates a full overlay or resides in SplitView.
                // If AdaptiveMeetingScreen handles SplitView internally, we are good.
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
        }
    }
}
