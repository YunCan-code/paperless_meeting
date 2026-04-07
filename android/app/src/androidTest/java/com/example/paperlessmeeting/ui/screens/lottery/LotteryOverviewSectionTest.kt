package com.example.paperlessmeeting.ui.screens.lottery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.paperlessmeeting.domain.model.LotteryHistoryResponse
import com.example.paperlessmeeting.domain.model.LotteryParticipant
import com.example.paperlessmeeting.domain.model.LotteryRound
import com.example.paperlessmeeting.domain.model.LotterySession
import com.example.paperlessmeeting.domain.model.LotteryWinner
import com.example.paperlessmeeting.domain.model.Meeting
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class LotteryOverviewSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun 宽屏_idle_状态可以稳定渲染() {
        setWideContent(
            LotteryListUiState(
                currentDisplayMeeting = testMeeting(),
                currentDisplaySession = testIdleSession()
            )
        )

        assertHasText("当前抽签")
        assertHasText("等待主持人开始抽签")
        assertHasText("中签结果")
        assertHasText("加入抽签池")
        assertLacksText("抽签开始后，无法退出或加入。")
        assertPanelsHeightAligned()
    }

    @Test
    fun 宽屏_rolling_状态可以稳定渲染() {
        setWideContent(
            LotteryListUiState(
                currentDisplayMeeting = testMeeting(),
                currentDisplaySession = testRollingSession()
            )
        )

        assertHasText("抽签进行中")
        assertHasText("张三")
    }

    @Test
    fun 宽屏_单人rolling_状态三列持续有内容() {
        setWideContent(
            LotteryListUiState(
                currentDisplayMeeting = testMeeting(),
                currentDisplaySession = testSingleParticipantRollingSession()
            )
        )

        val nodes = composeRule.onAllNodesWithText("单人代表").fetchSemanticsNodes()
        assertTrue("单人滚动时至少应渲染三处姓名以覆盖三列", nodes.size >= 3)
    }

    @Test
    fun 宽屏_双人rolling_状态少人场景仍可稳定渲染() {
        setWideContent(
            LotteryListUiState(
                currentDisplayMeeting = testMeeting(),
                currentDisplaySession = testTwoParticipantRollingSession()
            )
        )

        assertHasText("甲")
        assertHasText("乙")
    }

    @Test
    fun 宽屏_result_状态可以稳定渲染结果区() {
        setWideContent(
            LotteryListUiState(
                currentDisplayMeeting = testMeeting(),
                currentDisplaySession = testResultSession(),
                currentDisplayResultRound = testResultRound(),
                historyLotteries = listOf(testHistory())
            )
        )

        assertHasText("当前结果")
        assertHasText("王五")
        assertHasText("赵六")
        assertHasText("抽取 2 人")
        assertHasText("中签名单")
        assertLacksText("2026 年抽签演示会")
        assertLacksText("结果展示中")
        assertPanelsHeightAligned()
    }

    @Test
    fun 宽屏_completed_状态可以稳定渲染历史结果() {
        setWideContent(
            LotteryListUiState(
                currentDisplayMeeting = testMeeting(),
                currentDisplaySession = testCompletedSession(),
                currentDisplayResultRound = testResultRound(),
                historyLotteries = listOf(testHistory())
            )
        )

        assertHasText("抽签结束")
        assertHasText("当前结果")
        assertHasText("钱七")
        assertLacksText("本场抽签已结束")
    }

    @Test
    fun 宽屏_单人结果_状态使用满宽卡片展示() {
        setWideContent(
            LotteryListUiState(
                currentDisplayMeeting = testMeeting(),
                currentDisplaySession = testSingleWinnerResultSession(),
                currentDisplayResultRound = testSingleWinnerResultRound()
            )
        )

        assertHasText("当前结果")
        assertHasText("抽取 1 人")
        assertHasText("单人代表")
    }

    @Test
    fun 宽屏_已参与待开始_状态顶部显示退出按钮() {
        setWideContent(
            LotteryListUiState(
                currentDisplayMeeting = testMeeting(),
                currentDisplaySession = testJoinedIdleSession()
            )
        )

        assertHasText("退出抽签池")
    }

    @Test
    fun 宽屏_帮助按钮点击后显示提示文案() {
        setWideContent(
            LotteryListUiState(
                currentDisplayMeeting = testMeeting(),
                currentDisplaySession = testIdleSession()
            )
        )

        composeRule.onNodeWithTag(LotteryHeaderHelpButtonTestTag).performClick()

        assertHasText("提示")
        assertHasText("抽签开始后，无法退出或加入")
        assertLacksText("抽签开始后，无法退出或加入。")
    }

    @Test
    fun 宽屏_结果较多时仍可稳定渲染完整结果列表() {
        val manyRounds = (1..8).map { order ->
            LotteryRound(
                id = 200 + order,
                title = "第${order}轮抽签结果",
                count = 1,
                sort_order = order,
                status = "finished",
                winners = listOf(
                    LotteryWinner(
                        id = 300 + order,
                        user_id = 400 + order,
                        user_name = "成员$order"
                    )
                )
            )
        }
        val latestRound = manyRounds.last()

        setWideContent(
            LotteryListUiState(
                currentDisplayMeeting = testMeeting(),
                currentDisplaySession = LotterySession(
                    meeting_id = 101,
                    session_status = "completed",
                    current_round_id = latestRound.id,
                    current_round = latestRound,
                    winners = latestRound.winners,
                    participants_count = 8,
                    joined = true,
                    all_rounds_finished = true,
                    rounds = manyRounds
                ),
                currentDisplayResultRound = latestRound
            )
        )

        assertHasText("当前结果")
        assertHasText("第8轮抽签结果")
        assertHasText("成员8")

        composeRule
            .onNodeWithTag(LotteryWideResultListTestTag)
            .performScrollToNode(hasText("第1轮抽签结果"))

        assertHasText("第1轮抽签结果")
        assertHasText("成员1")
    }

    @Test
    fun 宽屏_无当前会议时展示空态且不崩溃() {
        setWideContent(LotteryListUiState())

        assertHasText("当前暂无可展示会议")
        assertHasText("暂无结果")
    }

    private fun setWideContent(uiState: LotteryListUiState) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(900.dp)) {
                    LotteryOverviewSection(
                        uiState = uiState,
                        onJoin = {},
                        onQuit = {},
                        forceWideLayout = true
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
    }

    private fun assertHasText(text: String) {
        val nodes = composeRule.onAllNodesWithText(text).fetchSemanticsNodes()
        assertTrue("未找到文本: $text", nodes.isNotEmpty())
    }

    private fun assertLacksText(text: String) {
        val nodes = composeRule.onAllNodesWithText(text).fetchSemanticsNodes()
        assertTrue("不应出现文本: $text", nodes.isEmpty())
    }

    private fun assertPanelsHeightAligned() {
        val leftHeight = composeRule
            .onNodeWithTag(LotteryCurrentPanelTestTag)
            .fetchSemanticsNode()
            .boundsInRoot
            .height
        val rightHeight = composeRule
            .onNodeWithTag(LotteryResultPanelTestTag)
            .fetchSemanticsNode()
            .boundsInRoot
            .height

        assertTrue(
            "左右卡片高度未对齐: left=$leftHeight, right=$rightHeight",
            abs(leftHeight - rightHeight) <= 1f
        )
    }

    private fun testMeeting(): Meeting {
        return Meeting(
            id = 101,
            title = "2026 年抽签演示会",
            meetingTypeId = 1,
            startTime = "2026-04-07 10:00:00",
            endTime = "2026-04-07 12:00:00",
            location = "一号会议室",
            host = "主持人"
        )
    }

    private fun testIdleSession(): LotterySession {
        val round = LotteryRound(
            id = 1,
            title = "第一轮抽签",
            count = 2,
            sort_order = 1,
            status = "draft"
        )
        return LotterySession(
            meeting_id = 101,
            session_status = "idle",
            self_service_open = true,
            next_round_id = round.id,
            next_round = round,
            rounds = listOf(round)
        )
    }

    private fun testJoinedIdleSession(): LotterySession {
        val round = LotteryRound(
            id = 11,
            title = "已加入待开始轮次",
            count = 2,
            sort_order = 1,
            status = "draft"
        )
        return LotterySession(
            meeting_id = 101,
            session_status = "idle",
            self_service_open = true,
            next_round_id = round.id,
            next_round = round,
            participants_count = 3,
            joined = true,
            rounds = listOf(round)
        )
    }

    private fun testRollingSession(): LotterySession {
        val round = LotteryRound(
            id = 2,
            title = "主席台抽签",
            count = 2,
            sort_order = 1,
            status = "rolling"
        )
        return LotterySession(
            meeting_id = 101,
            session_status = "rolling",
            current_round_id = round.id,
            current_round = round,
            participants = listOf(
                LotteryParticipant(id = 1, user_id = 1, name = "张三"),
                LotteryParticipant(id = 2, user_id = 2, name = "李四"),
                LotteryParticipant(id = 3, user_id = 3, name = "王五")
            ),
            participants_count = 3,
            joined = true,
            rounds = listOf(round)
        )
    }

    private fun testResultRound(): LotteryRound {
        return LotteryRound(
            id = 3,
            title = "第二轮抽签结果",
            count = 2,
            sort_order = 2,
            status = "finished",
            winners = listOf(
                LotteryWinner(id = 1, user_id = 5, user_name = "王五"),
                LotteryWinner(id = 2, user_id = 6, user_name = "赵六")
            )
        )
    }

    private fun testSingleParticipantRollingSession(): LotterySession {
        val round = LotteryRound(
            id = 20,
            title = "唯一候选人抽签",
            count = 1,
            sort_order = 1,
            status = "rolling"
        )
        return LotterySession(
            meeting_id = 101,
            session_status = "rolling",
            current_round_id = round.id,
            current_round = round,
            participants = listOf(
                LotteryParticipant(id = 10, user_id = 10, name = "单人代表")
            ),
            participants_count = 1,
            joined = true,
            rounds = listOf(round)
        )
    }

    private fun testSingleWinnerResultRound(): LotteryRound {
        return LotteryRound(
            id = 30,
            title = "单人中签结果",
            count = 1,
            sort_order = 3,
            status = "finished",
            winners = listOf(
                LotteryWinner(id = 30, user_id = 30, user_name = "单人代表")
            )
        )
    }

    private fun testSingleWinnerResultSession(): LotterySession {
        val resultRound = testSingleWinnerResultRound()
        return LotterySession(
            meeting_id = 101,
            session_status = "result",
            current_round_id = resultRound.id,
            current_round = resultRound.copy(winners = emptyList()),
            winners = resultRound.winners,
            participants = listOf(
                LotteryParticipant(id = 30, user_id = 30, name = "单人代表")
            ),
            participants_count = 1,
            joined = true,
            rounds = listOf(resultRound.copy(winners = emptyList()))
        )
    }

    private fun testTwoParticipantRollingSession(): LotterySession {
        val round = LotteryRound(
            id = 21,
            title = "双人抽签",
            count = 1,
            sort_order = 1,
            status = "rolling"
        )
        return LotterySession(
            meeting_id = 101,
            session_status = "rolling",
            current_round_id = round.id,
            current_round = round,
            participants = listOf(
                LotteryParticipant(id = 11, user_id = 11, name = "甲"),
                LotteryParticipant(id = 12, user_id = 12, name = "乙")
            ),
            participants_count = 2,
            joined = true,
            rounds = listOf(round)
        )
    }

    private fun testPreviousRound(): LotteryRound {
        return LotteryRound(
            id = 2,
            title = "第一轮抽签结果",
            count = 1,
            sort_order = 1,
            status = "finished",
            winners = listOf(LotteryWinner(id = 3, user_id = 7, user_name = "钱七"))
        )
    }

    private fun testResultSession(): LotterySession {
        val resultRound = testResultRound()
        return LotterySession(
            meeting_id = 101,
            session_status = "result",
            current_round_id = resultRound.id,
            current_round = resultRound.copy(winners = emptyList()),
            winners = resultRound.winners,
            participants = listOf(
                LotteryParticipant(id = 1, user_id = 5, name = "王五"),
                LotteryParticipant(id = 2, user_id = 6, name = "赵六")
            ),
            participants_count = 2,
            joined = true,
            rounds = listOf(testPreviousRound(), resultRound.copy(winners = emptyList()))
        )
    }

    private fun testCompletedSession(): LotterySession {
        val resultRound = testResultRound()
        return LotterySession(
            meeting_id = 101,
            session_status = "completed",
            current_round_id = resultRound.id,
            current_round = resultRound,
            winners = resultRound.winners,
            participants = listOf(
                LotteryParticipant(id = 1, user_id = 5, name = "王五"),
                LotteryParticipant(id = 2, user_id = 6, name = "赵六")
            ),
            participants_count = 2,
            joined = true,
            all_rounds_finished = true,
            rounds = listOf(testPreviousRound(), resultRound)
        )
    }

    private fun testHistory(): LotteryHistoryResponse {
        return LotteryHistoryResponse(
            meeting_id = 101,
            meeting_title = "2026 年抽签演示会",
            rounds = listOf(testPreviousRound(), testResultRound())
        )
    }
}
