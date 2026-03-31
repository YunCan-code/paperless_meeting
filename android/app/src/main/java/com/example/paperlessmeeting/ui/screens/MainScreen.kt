package com.example.paperlessmeeting.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.paperlessmeeting.ui.components.notice.AppNoticeHost
import com.example.paperlessmeeting.ui.components.notice.LocalAppNoticeController
import com.example.paperlessmeeting.ui.components.notice.rememberAppNoticeController
import com.example.paperlessmeeting.ui.navigation.MAIN_TABS_ROUTE
import com.example.paperlessmeeting.ui.navigation.Screen
import com.example.paperlessmeeting.ui.navigation.clearMainTabTransitionTarget
import com.example.paperlessmeeting.ui.navigation.mainTabTransitionTarget
import com.example.paperlessmeeting.ui.navigation.requestMainTabTransition
import com.example.paperlessmeeting.ui.screens.home.HomeViewModel
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val MainTabAnimationEasing = CubicBezierEasing(0.24f, 0.94f, 0.32f, 1f)
private const val MAIN_TAB_ANIMATION_DURATION_MS = 420
private const val EXIT_PROMPT_DURATION_MS = 2000L

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    onLogout: () -> Unit = {},
    onPortraitExemptionChanged: (Boolean) -> Unit = {},
    onExitApp: () -> Unit = {}
) {
    val navController = rememberNavController()
    val sharedMeetingViewModel: HomeViewModel = hiltViewModel()
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
    val appNoticeHostState = remember { SnackbarHostState() }
    val appNoticeController = rememberAppNoticeController(
        hostState = appNoticeHostState,
        scope = coroutineScope
    )
    var pendingExit by rememberSaveable { mutableStateOf(false) }
    var showExitPrompt by rememberSaveable { mutableStateOf(false) }

    val currentRoute = currentDestination?.route?.substringBefore("?")
    val isReaderScreen = currentRoute == "reader"
    val isVoteDetailScreen = currentRoute?.startsWith("vote_detail") == true
    val currentMainTabIndex = resolveCurrentMainTabIndex(
        currentRoute = currentRoute,
        pagerPage = pagerState.currentPage
    )
    val navScrollPosition = resolveNavBarScrollPosition(
        currentRoute = currentRoute,
        pagerScrollPosition = pagerState.currentPage + pagerState.currentPageOffsetFraction
    )

    fun animateToMainTab(targetPage: Int) {
        coroutineScope.launch {
            if (pagerState.currentPage == targetPage && pagerState.currentPageOffsetFraction == 0f) {
                return@launch
            }

            pagerState.animateScrollToPage(
                page = targetPage,
                animationSpec = tween(
                    durationMillis = MAIN_TAB_ANIMATION_DURATION_MS,
                    easing = MainTabAnimationEasing
                )
            )
        }
    }

    fun navigateToMainTab(targetPage: Int) {
        if (currentRoute == MAIN_TABS_ROUTE) {
            animateToMainTab(targetPage)
            return
        }

        navController.requestMainTabTransition(targetPage)
        navController.popBackStack(MAIN_TABS_ROUTE, inclusive = false)
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute != MAIN_TABS_ROUTE) {
            return@LaunchedEffect
        }

        val mainTabsEntry = navController.getBackStackEntry(MAIN_TABS_ROUTE)
        val targetPage = mainTabsEntry.savedStateHandle.mainTabTransitionTarget() ?: return@LaunchedEffect
        mainTabsEntry.savedStateHandle.clearMainTabTransitionTarget()
        animateToMainTab(targetPage)
    }

    LaunchedEffect(showExitPrompt) {
        if (!showExitPrompt) {
            return@LaunchedEffect
        }

        delay(EXIT_PROMPT_DURATION_MS)
        showExitPrompt = false
        pendingExit = false
    }

    LaunchedEffect(currentRoute, currentMainTabIndex) {
        if (currentRoute == MAIN_TABS_ROUTE && currentMainTabIndex == 0) {
            return@LaunchedEffect
        }

        showExitPrompt = false
        pendingExit = false
    }

    SideEffect {
        onPortraitExemptionChanged(isReaderScreen)
    }

    BackHandler(enabled = shouldHandleMainTabBack(currentRoute, currentMainTabIndex)) {
        when {
            currentMainTabIndex == null -> Unit
            currentMainTabIndex != 0 -> {
                showExitPrompt = false
                pendingExit = false
                navigateToMainTab(0)
            }
            pendingExit -> {
                showExitPrompt = false
                pendingExit = false
                onExitApp()
            }
            else -> {
                pendingExit = true
                showExitPrompt = true
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        CompositionLocalProvider(LocalAppNoticeController provides appNoticeController) {
            Box(modifier = Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = MAIN_TABS_ROUTE,
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars)
                ) {
                composable(MAIN_TABS_ROUTE) {
                    HorizontalPager(
                        state = pagerState,
                        beyondBoundsPageCount = 1,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).coerceIn(-1f, 1f)
                        val distanceFromCenter = abs(pageOffset)
                        val pageAlpha = 0.9f + ((1f - distanceFromCenter) * 0.1f)
                        val pageScale = 0.985f + ((1f - distanceFromCenter) * 0.015f)
                        val pageTranslationX = with(LocalDensity.current) { 28.dp.toPx() } * pageOffset

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = pageAlpha
                                    scaleX = pageScale
                                    scaleY = pageScale
                                    translationX = pageTranslationX
                                }
                        ) {
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
                                    }
                                )

                                1 -> com.example.paperlessmeeting.ui.screens.adaptive.AdaptiveMeetingScreen(
                                    meetingTypeName = "ALL",
                                    navController = navController,
                                    isActive = currentRoute == MAIN_TABS_ROUTE && currentMainTabIndex == 1,
                                    onNavigateToMedia = {
                                        navigateToMainTab(2)
                                    },
                                    viewModel = sharedMeetingViewModel
                                )

                                2 -> com.example.paperlessmeeting.ui.screens.media.MediaScreen()

                                3 -> com.example.paperlessmeeting.ui.screens.settings.SettingsScreen(
                                    onLogout = onLogout
                                )
                            }
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
                        initialMeetingId = meetingId,
                        isActive = true,
                        onNavigateToMedia = {
                            navigateToMainTab(2)
                        },
                        viewModel = sharedMeetingViewModel
                    )
                }

                composable(
                    route = "meeting_split/{typeName}",
                    arguments = listOf(
                        androidx.navigation.navArgument("typeName") {
                            type = androidx.navigation.NavType.StringType
                        }
                    )
                ) { backStackEntry ->
                    val typeName = backStackEntry.arguments?.getString("typeName") ?: "ALL"
                    com.example.paperlessmeeting.ui.screens.adaptive.AdaptiveMeetingScreen(
                        meetingTypeName = typeName,
                        navController = navController,
                        isActive = true,
                        onNavigateToMedia = {
                            navigateToMainTab(2)
                        },
                        viewModel = sharedMeetingViewModel
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

                AppNoticeHost(
                    hostState = appNoticeHostState,
                    hasFloatingNav = !isReaderScreen && !isVoteDetailScreen,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                AnimatedVisibility(
                    visible = showExitPrompt &&
                        currentRoute == MAIN_TABS_ROUTE &&
                        currentMainTabIndex == 0 &&
                        !isReaderScreen,
                    enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(bottom = 76.dp)
                ) {
                    ExitPromptPill()
                }

                AnimatedVisibility(
                    visible = !isReaderScreen && !isVoteDetailScreen,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    FloatingNavBar(
                        tabs = tabs,
                        scrollPosition = navScrollPosition,
                        onTabClick = { screen ->
                            val index = tabs.indexOf(screen)
                            navigateToMainTab(index)
                        }
                    )
                }
            }
        }
    }
}

private fun resolveCurrentMainTabIndex(
    currentRoute: String?,
    pagerPage: Int
): Int? {
    val route = currentRoute ?: return null
    return when {
        route == MAIN_TABS_ROUTE -> pagerPage
        route.startsWith(Screen.Meetings.route) -> 1
        route.startsWith("meeting_split") -> 1
        else -> null
    }
}

private fun shouldHandleMainTabBack(
    currentRoute: String?,
    currentMainTabIndex: Int?
): Boolean {
    if (currentMainTabIndex == null) {
        return false
    }

    return currentRoute == MAIN_TABS_ROUTE ||
        currentRoute?.startsWith(Screen.Meetings.route) == true ||
        currentRoute?.startsWith("meeting_split") == true
}

private fun resolveNavBarScrollPosition(
    currentRoute: String?,
    pagerScrollPosition: Float
): Float {
    val route = currentRoute ?: return pagerScrollPosition
    return when {
        route == MAIN_TABS_ROUTE -> pagerScrollPosition
        route.startsWith(Screen.Meetings.route) -> 1f
        route.startsWith("meeting_split") -> 1f
        route.startsWith("detail") -> 1f
        route.startsWith(Screen.Media.route) -> 2f
        route.startsWith(Screen.Settings.route) -> 3f
        else -> pagerScrollPosition
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

    val itemWidth = if (isPhone) 78.dp else 92.dp
    val itemHeight = if (isPhone) 52.dp else 56.dp
    val horizontalPadding = if (isPhone) 8.dp else 12.dp
    val verticalPadding = 4.dp
    val itemSpacing = if (isPhone) 4.dp else 16.dp
    val tabFrames = remember(tabs.size) {
        MutableList(tabs.size) { NavItemFrame.Zero }
    }
    val clampedScrollPosition = scrollPosition.coerceIn(0f, tabs.lastIndex.toFloat())
    val leftIndex = floor(clampedScrollPosition).toInt()
    val rightIndex = ceil(clampedScrollPosition).toInt().coerceAtMost(tabs.lastIndex)
    val positionFraction = clampedScrollPosition - leftIndex
    val leftFrame = tabFrames.getOrNull(leftIndex) ?: NavItemFrame.Zero
    val rightFrame = tabFrames.getOrNull(rightIndex) ?: leftFrame
    val indicatorOffsetPx = lerpFloat(leftFrame.left, rightFrame.left, positionFraction)
    val indicatorWidthPx = lerpFloat(leftFrame.width, rightFrame.width, positionFraction)
    val density = LocalDensity.current
    val fallbackIndicatorOffsetPx = with(density) {
        (horizontalPadding + (itemWidth + itemSpacing) * clampedScrollPosition).roundToPx().toFloat()
    }
    val fallbackIndicatorWidthPx = with(density) { itemWidth.roundToPx().toFloat() }
    val resolvedIndicatorOffsetPx =
        if (leftFrame == NavItemFrame.Zero && rightFrame == NavItemFrame.Zero) fallbackIndicatorOffsetPx else indicatorOffsetPx
    val resolvedIndicatorWidth = with(density) {
        (if (indicatorWidthPx == 0f) fallbackIndicatorWidthPx else indicatorWidthPx).toDp()
    }

    Surface(
        shape = navPillShape,
        shadowElevation = 4.dp,
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.padding(bottom = if (isPhone) 8.dp else 16.dp)
    ) {
        Box(
            modifier = Modifier.padding(
                horizontal = horizontalPadding,
                vertical = verticalPadding
            )
        ) {
            Box(
                modifier = Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(x = resolvedIndicatorOffsetPx.roundToInt(), y = 0) }
                    .width(resolvedIndicatorWidth)
                    .height(itemHeight)
                    .graphicsLayer {
                        alpha = 0.98f
                        scaleX = 0.992f
                        scaleY = 0.992f
                    }
                    .clip(navPillShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, screen ->
                    val selectionFraction = (1f - abs(scrollPosition - index)).coerceIn(0f, 1f)
                    FloatingNavItem(
                        icon = screen.icon,
                        label = screen.title,
                        selectionFraction = selectionFraction,
                        onClick = { onTabClick(screen) },
                        isPhone = isPhone,
                        shape = navPillShape,
                        itemWidth = itemWidth,
                        itemHeight = itemHeight,
                        onPositioned = { left, width ->
                            tabFrames[index] = NavItemFrame(left = left, width = width)
                        }
                    )
                }
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
    shape: RoundedCornerShape = RoundedCornerShape(50),
    itemWidth: androidx.compose.ui.unit.Dp,
    itemHeight: androidx.compose.ui.unit.Dp,
    onPositioned: (left: Float, width: Float) -> Unit
) {
    val selectedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    val contentColor = lerpColor(unselectedColor, selectedColor, selectionFraction)

    val horizontalPadding = if (isPhone) 10.dp else 12.dp
    val verticalPadding = 7.dp

    Box(
        modifier = Modifier
            .width(itemWidth)
            .height(itemHeight)
            .onGloballyPositioned { coordinates ->
                onPositioned(coordinates.positionInParent().x, coordinates.size.width.toFloat())
            }
            .clip(shape)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
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

private data class NavItemFrame(
    val left: Float,
    val width: Float
) {
    companion object {
        val Zero = NavItemFrame(left = 0f, width = 0f)
    }
}

private fun lerpFloat(start: Float, end: Float, fraction: Float): Float {
    return start + ((end - start) * fraction)
}

@Composable
private fun ExitPromptPill() {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        tonalElevation = 5.dp,
        shadowElevation = 12.dp
    ) {
        Text(
            text = "再按一次退出程序",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
        )
    }
}
