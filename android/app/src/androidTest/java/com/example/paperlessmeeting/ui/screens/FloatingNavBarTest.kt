package com.example.paperlessmeeting.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.paperlessmeeting.ui.navigation.Screen
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FloatingNavBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun 手机端首页选中时左侧留白收紧到接近四dp() {
        composeRule.setContent {
            MaterialTheme {
                FloatingNavBarTestHost(
                    tabs = listOf(
                        Screen.Dashboard,
                        Screen.Meetings,
                        Screen.Media,
                        Screen.Settings
                    ),
                    scrollPosition = 0f
                )
            }
        }

        composeRule.waitForIdle()

        composeRule.onNodeWithText("首页").fetchSemanticsNode()
        composeRule.onNodeWithText("会议").fetchSemanticsNode()
        composeRule.onNodeWithText("媒体").fetchSemanticsNode()
        composeRule.onNodeWithText("设置").fetchSemanticsNode()

        val navBounds = composeRule
            .onNodeWithTag(FloatingNavBarContainerTestTag)
            .fetchSemanticsNode()
            .boundsInRoot
        val indicatorBounds = composeRule
            .onNodeWithTag(FloatingNavBarIndicatorTestTag)
            .fetchSemanticsNode()
            .boundsInRoot

        val leftInsetPx = indicatorBounds.left - navBounds.left
        val expectedInsetPx = with(composeRule.density) { 4.dp.toPx() }

        assertTrue(
            "首页选中态左侧留白过大: actual=$leftInsetPx, expected≈$expectedInsetPx",
            abs(leftInsetPx - expectedInsetPx) <= with(composeRule.density) { 1.dp.toPx() }
        )
    }
}
