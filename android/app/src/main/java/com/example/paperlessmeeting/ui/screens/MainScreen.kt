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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.paperlessmeeting.ui.navigation.Screen
import kotlin.math.abs
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
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

    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()

    val isReaderScreen = currentDestination?.route?.startsWith("reader") == true

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Content
            NavHost(
                navController = navController,
                startDestination = "main_tabs",
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
            ) {
                // 主标签页：HorizontalPager 包裹 4 个页面
                composable("main_tabs") {
                    HorizontalPager(
                        state = pagerState,
                        beyondBoundsPageCount = 1,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        when (page) {
                            0 -> com.example.paperlessmeeting.ui.screens.dashboard.DashboardScreen(
                                onMeetingClick = { meetingId ->
                                    navController.navigate("meetings?meetingId=$meetingId")
                                },
                                onReadingClick = { url, name, pageNum ->
                                    val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
                                    val encodedName = java.net.URLEncoder.encode(name, "UTF-8")
                                    navController.navigate("reader?url=$encodedUrl&name=$encodedName&page=$pageNum")
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
                            1 -> com.example.paperlessmeeting.ui.screens.adaptive.AdaptiveMeetingScreen(
                                meetingTypeName = "ALL",
                                navController = navController
                            )
                            2 -> com.example.paperlessmeeting.ui.screens.media.MediaScreen()
                            3 -> com.example.paperlessmeeting.ui.screens.settings.SettingsScreen(
                                navController = navController,
                                onLogout = onLogout
                            )
                        }
                    }
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
                    scrollPosition = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                    onTabClick = { screen ->
                        val index = tabs.indexOf(screen)
                        // 如果在子路由上，先返回 main_tabs
                        navController.popBackStack("main_tabs", inclusive = false)
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
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
    scrollPosition: Float,
    onTabClick: (Screen) -> Unit
) {
    val isPhone = LocalConfiguration.current.screenWidthDp < 600
    val navPillShape = RoundedCornerShape(50)

    Surface(
        shape = navPillShape,
        shadowElevation = 4.dp,
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
            tabs.forEachIndexed { index, screen ->
                // 根据 pager 滑动位置计算每个 tab 的选中分数 (0f ~ 1f)
                val selectionFraction = (1f - abs(scrollPosition - index)).coerceIn(0f, 1f)
                FloatingNavItem(
                    icon = screen.icon,
                    label = screen.title,
                    selectionFraction = selectionFraction,
                    onClick = { onTabClick(screen) },
                    isPhone = isPhone,
                    shape = navPillShape
                )
            }
        }
    }
}

@Composable
private fun FloatingNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selectionFraction: Float,
    onClick: () -> Unit,
    isPhone: Boolean = false,
    shape: RoundedCornerShape = RoundedCornerShape(50)
) {
    // 直接使用 selectionFraction 驱动所有视觉属性，跟随手指平滑过渡
    val capsuleAlpha = selectionFraction
    val capsuleScale = 0.96f + 0.04f * selectionFraction

    val selectedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    val contentColor = lerpColor(unselectedColor, selectedColor, selectionFraction)

    val itemWidth = if (isPhone) 78.dp else 92.dp
    val itemHeight = if (isPhone) 52.dp else 56.dp
    val horizontalPadding = if (isPhone) 10.dp else 12.dp
    val verticalPadding = 7.dp
    Box(
        modifier = Modifier
            .width(itemWidth)
            .height(itemHeight)
            .clip(shape)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = capsuleAlpha
                    scaleX = capsuleScale
                    scaleY = capsuleScale
                }
                .clip(shape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(
                horizontal = horizontalPadding,
                vertical = verticalPadding
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
                fontWeight = if (selectionFraction > 0.5f) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}
