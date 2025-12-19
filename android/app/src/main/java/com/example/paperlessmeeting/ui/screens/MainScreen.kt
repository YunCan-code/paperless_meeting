package com.example.paperlessmeeting.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                // Top items (Dashboard, Meetings)
                items.filter { it != Screen.Settings }.forEach { screen ->
                    NavigationRailItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.route == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
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
                            restoreState = true
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp)) // Bottom padding
            }

            // === Main Content Area (Right) ===
            NavHost(
                navController = navController,
                startDestination = Screen.Dashboard.route,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            ) {
                composable(Screen.Dashboard.route) {
                    // Placeholder for Dashboard
                    com.example.paperlessmeeting.ui.screens.dashboard.DashboardScreen()
                }
                composable(Screen.Meetings.route) {
                    // We reuse AdaptiveMeetingScreen but maybe tweak it to accept internal filter selection
                    // For now, defaulting to "General" or showing ALL (need to update AdaptiveScreen logic)
                    com.example.paperlessmeeting.ui.screens.adaptive.AdaptiveMeetingScreen(
                        meetingTypeName = "ALL", // Signal to show filterable UI
                        navController = navController
                    )
                }
                composable(Screen.Settings.route) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text("Settings Placeholder", modifier = Modifier.align(Alignment.Center))
                    }
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
            }
        }
    }
}
