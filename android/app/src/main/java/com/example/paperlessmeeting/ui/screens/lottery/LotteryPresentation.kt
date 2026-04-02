package com.example.paperlessmeeting.ui.screens.lottery

import com.example.paperlessmeeting.domain.model.LotteryRound
import com.example.paperlessmeeting.domain.model.LotterySession

private val lotteryRoundNumberMap = listOf("零", "一", "二", "三", "四", "五", "六", "七", "八", "九")

fun formatLotteryRoundOrder(sortOrder: Int?): String {
    val value = sortOrder ?: 0
    if (value <= 0) return "未排轮次"
    if (value < 10) return "第${lotteryRoundNumberMap[value]}轮"
    if (value == 10) return "第十轮"
    if (value < 20) return "第十${lotteryRoundNumberMap[value - 10]}轮"
    val tens = value / 10
    val units = value % 10
    val tensLabel = lotteryRoundNumberMap.getOrNull(tens) ?: tens.toString()
    val unitsLabel = if (units == 0) "" else lotteryRoundNumberMap.getOrNull(units) ?: units.toString()
    return "第${tensLabel}十${unitsLabel}轮"
}

fun LotterySession.sortedRounds(): List<LotteryRound> {
    return rounds.sortedWith(compareBy<LotteryRound> { if (it.sort_order > 0) 0 else 1 }.thenBy { it.sort_order }.thenBy { it.id })
}

fun LotterySession.displayRound(): LotteryRound? {
    return current_round ?: next_round ?: sortedRounds().firstOrNull { it.status != "finished" }
}

fun LotterySession.finishedRounds(): List<LotteryRound> {
    return sortedRounds().filter { it.status == "finished" || it.winners.isNotEmpty() }
}

fun LotterySession.remainingRoundsCount(): Int {
    return sortedRounds().count { it.status != "finished" }
}

fun LotterySession.statusLabel(): String {
    return when (session_status) {
        "idle" -> "空闲"
        "collecting" -> "收集中"
        "ready" -> "准备就绪"
        "rolling" -> "滚动中"
        "result" -> "结果展示中"
        "completed" -> "全部完成"
        else -> session_status
    }
}

fun LotterySession.roundSummaryLine(): String {
    val current = current_round
    val next = next_round
    return when {
        session_status == "rolling" && current != null -> "${formatLotteryRoundOrder(current.sort_order)}正在抽取"
        session_status in setOf("result", "completed") && current != null -> "${formatLotteryRoundOrder(current.sort_order)}结果保留中"
        next != null -> "下一轮：${formatLotteryRoundOrder(next.sort_order)} ${next.title}"
        else -> "等待主持人开始抽签"
    }
}

fun LotterySession.stageDescription(): String {
    val displayRound = displayRound()
    return when {
        session_status == "rolling" && displayRound != null -> "当前正在抽取${formatLotteryRoundOrder(displayRound.sort_order)}，请等待结果产生。"
        winners.isNotEmpty() && current_round != null -> "${formatLotteryRoundOrder(current_round.sort_order)}已产生 ${winners.size} 位中签人员。"
        displayRound != null -> "当前轮次为${formatLotteryRoundOrder(displayRound.sort_order)}，${displayRound.title}。"
        else -> "当前暂无可展示的抽签轮次。"
    }
}

fun LotteryRound.roundOrderLabel(): String = formatLotteryRoundOrder(sort_order)

fun LotteryRound.roundStatusLabel(session: LotterySession? = null): String {
    val currentRoundId = session?.current_round?.id
    val nextRoundId = session?.next_round?.id
    return when {
        session != null && currentRoundId == id && session.session_status == "rolling" -> "当前抽取"
        session != null && currentRoundId == id && session.session_status in setOf("result", "completed") -> "当前结果"
        session != null && nextRoundId == id -> "下一轮"
        status == "finished" || winners.isNotEmpty() -> "已抽取"
        else -> "待抽取"
    }
}
