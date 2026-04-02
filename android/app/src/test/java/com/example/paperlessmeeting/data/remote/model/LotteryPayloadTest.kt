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
            next_round = null,
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
        assertEquals(null, session.next_round_id)
        assertEquals(null, session.next_round)
        assertFalse(session.joined)
        assertTrue(session.self_service_open)
        assertFalse(session.all_rounds_finished)
    }

    @Test
    fun `LotterySessionPayload toDomain 正确保留轮次顺序 下一轮与中奖人信息`() {
        val payload = LotterySessionPayload(
            meeting_id = 9,
            session_status = "result",
            self_service_open = false,
            current_round_id = 3,
            current_round = LotteryRoundPayload(
                id = 3,
                title = "一等奖",
                count = 2,
                allow_repeat = true,
                sort_order = 1,
                status = "finished",
                winners = listOf(
                    LotteryWinnerPayload(id = 11, user_id = 11, user_name = "张三"),
                    LotteryWinnerPayload(user_id = 12, name = "李四")
                )
            ),
            next_round_id = 4,
            next_round = LotteryRoundPayload(
                id = 4,
                title = "二等奖",
                count = 1,
                allow_repeat = false,
                sort_order = 2,
                status = "draft"
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
                    sort_order = 1,
                    status = "finished"
                ),
                LotteryRoundPayload(
                    id = 4,
                    title = "二等奖",
                    count = 1,
                    allow_repeat = false,
                    sort_order = 2,
                    status = "draft"
                )
            )
        )

        val session = payload.toDomain()

        assertEquals("result", session.session_status)
        assertEquals(3, session.current_round_id)
        assertEquals("一等奖", session.current_round?.title)
        assertEquals(1, session.current_round?.sort_order)
        assertEquals(4, session.next_round_id)
        assertEquals("二等奖", session.next_round?.title)
        assertEquals(2, session.next_round?.sort_order)
        assertEquals(2, session.current_round?.winners?.size)
        assertEquals("王五", session.participants.first().name)
        assertEquals("张三", session.winners.first().user_name)
        assertTrue(session.joined)
        assertFalse(session.self_service_open)
        assertTrue(session.all_rounds_finished)
        assertEquals(2, session.rounds.size)
    }

    @Test
    fun `LotterySessionPayload toDomain 在仅提供 next_round_id 时仍能从 rounds 回填`() {
        val payload = LotterySessionPayload(
            meeting_id = 12,
            session_status = "ready",
            next_round_id = 8,
            rounds = listOf(
                LotteryRoundPayload(
                    id = 8,
                    title = "第三轮",
                    count = 1,
                    sort_order = 3,
                    status = "draft"
                )
            )
        )

        val session = payload.toDomain()

        assertEquals(8, session.next_round_id)
        assertEquals("第三轮", session.next_round?.title)
        assertEquals(3, session.next_round?.sort_order)
    }
}
