package com.example.paperlessmeeting.ui.screens.lottery

import com.example.paperlessmeeting.domain.model.LotteryRound
import com.example.paperlessmeeting.domain.model.LotterySession
import com.example.paperlessmeeting.domain.model.LotteryWinner
import com.example.paperlessmeeting.ui.components.lottery.buildLotterySlotColumns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LotteryPresentationLayoutTest {

    @Test
    fun `displayResultRounds 会把当前结果置顶并去重`() {
        val latestWinners = listOf(
            LotteryWinner(user_id = 1, user_name = "张三"),
            LotteryWinner(user_id = 2, user_name = "李四")
        )
        val currentRound = LotteryRound(
            id = 8,
            title = "第二轮",
            sort_order = 2,
            status = "finished",
            winners = latestWinners
        )
        val previousRound = LotteryRound(
            id = 7,
            title = "第一轮",
            sort_order = 1,
            status = "finished",
            winners = listOf(LotteryWinner(user_id = 3, user_name = "王五"))
        )
        val session = LotterySession(
            session_status = "completed",
            current_round = currentRound,
            winners = latestWinners,
            rounds = listOf(previousRound, currentRound)
        )

        val displayRounds = session.displayResultRounds()

        assertEquals(listOf(8, 7), displayRounds.map { it.id })
        assertEquals(2, displayRounds.first().winners.size)
    }

    @Test
    fun `buildLotterySlotColumns 在单人场景生成三列非空长轨道`() {
        val columns = buildLotterySlotColumns(listOf("唯一候选人"))

        assertEquals(3, columns.size)
        assertTrue(columns.all { it.items.size >= 36 })
        assertTrue(columns.all { column -> column.items.all { it == "唯一候选人" } })
    }

    @Test
    fun `buildLotterySlotColumns 在少人场景保持列偏移和无缝循环`() {
        val columns = buildLotterySlotColumns(listOf("张三", "李四", "王五"))

        assertEquals(listOf("张三", "李四", "王五"), columns[0].items.take(3))
        assertEquals(listOf("李四", "王五", "张三"), columns[1].items.take(3))
        assertEquals(listOf("王五", "张三", "李四"), columns[2].items.take(3))
        assertTrue(columns.all { column ->
            val midpoint = column.items.size / 2
            midpoint > 0 && column.items.take(midpoint) == column.items.drop(midpoint)
        })
    }
}
