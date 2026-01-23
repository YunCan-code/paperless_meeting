package com.example.paperlessmeeting.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "首页", Icons.Default.Home)
    object Meetings : Screen("meetings", "会议", Icons.Default.DateRange)
    object Settings : Screen("settings", "设置", Icons.Default.Settings)
    object LotteryList : Screen("lottery_list", "抽签列表", Icons.Default.Home) // Icon not used for sub-screen
    object LotteryDetail : Screen("lottery_detail/{meetingId}/{title}", "抽签详情", Icons.Default.Home) {
        fun createRoute(meetingId: Int, title: String) = "lottery_detail/$meetingId/$title"
    }
}
