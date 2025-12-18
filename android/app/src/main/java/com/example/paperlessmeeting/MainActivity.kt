package com.example.paperlessmeeting

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.paperlessmeeting.ui.screens.home.HomeScreen
import com.example.paperlessmeeting.ui.theme.PaperlessMeetingTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Prepare for edge-to-edge
        setContent {
            PaperlessMeetingTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(navController = navController)
                        }
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
    }
}
