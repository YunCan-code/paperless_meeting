package com.example.paperlessmeeting.ui.screens.lottery

import com.example.paperlessmeeting.domain.model.LotteryRound
import com.example.paperlessmeeting.domain.model.LotterySession
import com.example.paperlessmeeting.domain.model.LotteryWinner
import com.example.paperlessmeeting.domain.model.Meeting
import com.example.paperlessmeeting.ui.components.lottery.LotteryChipTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LotteryPresentationTest {

    @Test
    fun `displayRound 在没有 current_round 时回退到 next_round`() {
        val session = LotterySession(
            session_status = "ready",
            next_round = LotteryRound(
                id = 2,
                title = "第二轮",
                sort_order = 2,
                status = "draft"
            ),
            rounds = listOf(
                LotteryRound(id = 1, title = "第一轮", sort_order = 1, status = "finished"),
                LotteryRound(id = 2, title = "第二轮", sort_order = 2, status = "draft")
            )
        )

        val displayRound = session.displayRound()

        assertNotNull(displayRound)
        assertEquals(2, displayRound?.id)
    }

    @Test
    fun `roundStatusLabel 在结果态为当前轮返回 当前结果`() {
        val round = LotteryRound(
            id = 3,
            title = "主席台抽签",
            sort_order = 3,
            status = "finished"
        )
        val session = LotterySession(
            session_status = "result",
            current_round = round,
            current_round_id = 3
        )

        assertEquals("当前结果", round.roundStatusLabel(session))
        assertEquals(LotteryChipTone.Warning, round.roundStatusTone(session))
    }

    @Test
    fun `actionState 在已入池且可退出时返回退出按钮`() {
        val session = LotterySession(
            session_status = "ready",
            joined = true,
            current_round = LotteryRound(
                id = 1,
                title = "第一轮",
                sort_order = 1,
                status = "ready"
            )
        )

        val actionState = session.actionState()

        assertEquals("退出抽签池", actionState.secondaryLabel)
        assertEquals(true, actionState.secondaryEnabled)
        assertEquals(LotteryChipTone.Success, actionState.tone)
    }

    @Test
    fun `currentResultRound 在结果态回填 winners`() {
        val currentRound = LotteryRound(
            id = 5,
            title = "第五轮",
            sort_order = 5,
            status = "finished"
        )
        val session = LotterySession(
            session_status = "result",
            current_round = currentRound,
            winners = listOf(LotteryWinner(user_id = 7, user_name = "张三"))
        )

        val resultRound = session.currentResultRound()

        assertNotNull(resultRound)
        assertEquals(1, resultRound?.winners?.size)
        assertEquals("中签名单：张三", resultRound?.winnerNamesSummary())
    }

    @Test
    fun `selectCurrentDisplayMeeting 优先选择可抽状态会议`() {
        val fallbackMeeting = Meeting(
            id = 1,
            title = "上午例会",
            meetingTypeId = 1,
            startTime = "2026-04-02 09:00:00",
            endTime = "2026-04-02 10:00:00",
            location = null,
            host = null
        )
        val preferredMeeting = Meeting(
            id = 2,
            title = "下午抽签会",
            meetingTypeId = 1,
            startTime = "2026-04-02 14:00:00",
            endTime = "2026-04-02 15:00:00",
            location = null,
            host = null
        )

        val selected = selectCurrentDisplayMeeting(
            listOf(
                LotteryMeetingCardItem(
                    meeting = fallbackMeeting,
                    session = LotterySession(session_status = "completed")
                ),
                LotteryMeetingCardItem(
                    meeting = preferredMeeting,
                    session = LotterySession(session_status = "rolling")
                )
            )
        )

        assertEquals(2, selected?.meeting?.id)
    }

    @Test
    fun `resolveCurrentDisplayResultRound 在无当前结果时回退最近完成轮次`() {
        val session = LotterySession(
            session_status = "ready",
            rounds = listOf(
                LotteryRound(
                    id = 1,
                    title = "第一轮",
                    sort_order = 1,
                    status = "finished",
                    winners = listOf(LotteryWinner(user_id = 1, user_name = "张三"))
                ),
                LotteryRound(
                    id = 2,
                    title = "第二轮",
                    sort_order = 2,
                    status = "finished",
                    winners = listOf(LotteryWinner(user_id = 2, user_name = "李四"))
                )
            )
        )

        val resultRound = resolveCurrentDisplayResultRound(session)

        assertNotNull(resultRound)
        assertEquals(2, resultRound?.id)
    }

    @Test
    fun `resolveCurrentDisplayResultRound 在无会话结果时回退历史结果`() {
        val history = com.example.paperlessmeeting.domain.model.LotteryHistoryResponse(
            meeting_id = 7,
            meeting_title = "抽签会",
            rounds = listOf(
                LotteryRound(
                    id = 3,
                    title = "第三轮",
                    sort_order = 3,
                    status = "finished",
                    winners = listOf(LotteryWinner(user_id = 8, user_name = "王五"))
                )
            )
        )

        val resultRound = resolveCurrentDisplayResultRound(session = null, history = history)

        assertNotNull(resultRound)
        assertEquals(3, resultRound?.id)
    }

    @Test
    fun `resolveCurrentDisplayResultRound 在没有可展示结果时返回空`() {
        val session = LotterySession(session_status = "collecting")

        val resultRound = resolveCurrentDisplayResultRound(session)

        assertNull(resultRound)
    }
}
