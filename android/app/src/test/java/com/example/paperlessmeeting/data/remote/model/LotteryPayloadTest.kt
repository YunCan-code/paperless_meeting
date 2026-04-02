package com.example.paperlessmeeting.data.remote.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LotteryPayloadTest {

    @Test
    fun `LotterySessionPayload toDomain 为缺失列表字段提供默认值`() {
        val payload = LotterySessionPayload(
            meeting_id = 7,
            session_status = null,
            participants = null,
            winners = null,
            rounds = null,
            joined = null,
            all_rounds_finished = null
        )

        val session = payload.toDomain()

        assertEquals(7, session.meeting_id)
        assertEquals("idle", session.session_status)
        assertTrue(session.participants.isEmpty())
        assertTrue(session.winners.isEmpty())
        assertTrue(session.rounds.isEmpty())
        assertFalse(session.joined)
        assertFalse(session.all_rounds_finished)
    }

    @Test
    fun `LotterySessionPayload toDomain 正确保留轮次与中奖人信息`() {
        val payload = LotterySessionPayload(
            meeting_id = 9,
            session_status = "result",
            current_round_id = 3,
            current_round = LotteryRoundPayload(
                id = 3,
                title = "一等奖",
                count = 2,
                allow_repeat = true,
                status = "finished",
                winners = listOf(
                    LotteryWinnerPayload(id = 11, user_id = 11, user_name = "张三"),
                    LotteryWinnerPayload(user_id = 12, name = "李四")
                )
            ),
            participants = listOf(
                LotteryParticipantPayload(user_id = 21, name = "王五", is_winner = false)
            ),
            winners = listOf(LotteryWinnerPayload(user_id = 11, user_name = "张三")),
            joined = true,
            all_rounds_finished = true,
            rounds = listOf(
                LotteryRoundPayload(
                    id = 3,
                    title = "一等奖",
                    count = 2,
                    allow_repeat = true,
                    status = "finished"
                )
            )
        )

        val session = payload.toDomain()

        assertEquals("result", session.session_status)
        assertEquals(3, session.current_round_id)
        assertEquals("一等奖", session.current_round?.title)
        assertEquals(2, session.current_round?.winners?.size)
        assertEquals("王五", session.participants.first().name)
        assertEquals("张三", session.winners.first().user_name)
        assertTrue(session.joined)
        assertTrue(session.all_rounds_finished)
        assertEquals(1, session.rounds.size)
    }
}
