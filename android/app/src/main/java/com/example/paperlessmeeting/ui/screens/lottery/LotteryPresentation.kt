package com.example.paperlessmeeting.ui.screens.lottery

import com.example.paperlessmeeting.domain.model.LotteryRound
import com.example.paperlessmeeting.domain.model.LotterySession
import com.example.paperlessmeeting.domain.model.LotteryWinner
import com.example.paperlessmeeting.domain.model.LotteryHistoryResponse
import com.example.paperlessmeeting.domain.model.Meeting
import com.example.paperlessmeeting.ui.components.lottery.LotteryChipTone
import java.time.LocalDateTime

private val lotteryRoundNumberMap = listOf("零", "一", "二", "三", "四", "五", "六", "七", "八", "九")

data class LotteryHeaderState(
    val lifecycleLabel: String,
    val lifecycleTone: LotteryChipTone,
    val attendeeLabel: String,
    val attendeeTone: LotteryChipTone,
    val supportingText: String
)

data class LotteryActionState(
    val message: String,
    val tone: LotteryChipTone,
    val primaryLabel: String? = null,
    val primaryEnabled: Boolean = false,
    val secondaryLabel: String? = null,
    val secondaryEnabled: Boolean = false
)

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

fun selectCurrentDisplayMeeting(items: List<LotteryMeetingCardItem>): LotteryMeetingCardItem? {
    val sortedItems = items.sortedWith(
        compareByDescending<LotteryMeetingCardItem> { it.meeting.displaySortTime() ?: LocalDateTime.MIN }
            .thenByDescending { it.meeting.id }
    )

    return sortedItems.firstOrNull { it.session?.session_status in setOf("collecting", "ready", "rolling", "result") }
        ?: sortedItems.firstOrNull { it.session.hasDisplayableRounds() }
        ?: sortedItems.firstOrNull()
}

fun resolveCurrentDisplayResultRound(
    session: LotterySession?,
    history: LotteryHistoryResponse? = null
): LotteryRound? {
    session?.currentResultRound()?.let { return it }
    session?.finishedRounds()?.latestFinishedRound()?.let { return it }
    return history?.rounds?.latestFinishedRound()
}

fun LotterySession.sortedRounds(): List<LotteryRound> {
    return rounds.sortedWith(
        compareBy<LotteryRound> { if (it.sort_order > 0) 0 else 1 }
            .thenBy { it.sort_order }
            .thenBy { it.id }
    )
}

private fun Meeting.displaySortTime(): LocalDateTime? {
    return try {
        LocalDateTime.parse(startTime.replace(" ", "T"))
    } catch (_: Exception) {
        null
    }
}

private fun LotterySession?.hasDisplayableRounds(): Boolean {
    if (this == null) return false
    return current_round != null ||
        next_round != null ||
        rounds.isNotEmpty() ||
        finishedRounds().isNotEmpty()
}

private fun List<LotteryRound>.latestFinishedRound(): LotteryRound? {
    return this
        .filter { it.status == "finished" || it.winners.isNotEmpty() }
        .maxWithOrNull(
            compareBy<LotteryRound> { if (it.sort_order > 0) it.sort_order else Int.MIN_VALUE }
                .thenBy { it.id }
        )
}

fun LotterySession.displayRound(): LotteryRound? {
    return current_round ?: next_round ?: sortedRounds().firstOrNull { it.status != "finished" }
}

fun LotterySession.finishedRounds(): List<LotteryRound> {
    return sortedRounds().filter { it.status == "finished" || it.winners.isNotEmpty() }
}

fun LotterySession.mergePublicSessionUpdate(
    currentUserId: Int?,
    previousSession: LotterySession?
): LotterySession {
    val resolvedJoined = when {
        currentUserId != null -> participants.any { participant ->
            participant.user_id == currentUserId || participant.id == currentUserId
        }
        previousSession != null -> previousSession.joined
        else -> joined
    }

    return copy(joined = resolvedJoined)
}

fun LotterySession.currentResultRound(): LotteryRound? {
    val round = current_round ?: return null
    if (winners.isEmpty() && round.winners.isEmpty()) return null
    return if (session_status in setOf("result", "completed") || round.status == "finished") {
        if (round.winners.isNotEmpty()) round else round.copy(winners = winners)
    } else {
        null
    }
}

fun LotterySession.remainingRoundsCount(): Int {
    return sortedRounds().count { it.status != "finished" }
}

fun LotterySession.statusLabel(): String {
    return when (session_status) {
        "idle", "collecting", "ready" -> "待开始"
        "rolling" -> "抽签进行中"
        "result" -> "当前结果"
        "completed" -> "抽签结束"
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

fun LotterySession.allowRepeatLabel(): String {
    return if (displayRound()?.allow_repeat == true) "允许重复抽签" else "不允许重复抽签"
}

fun LotterySession.headerState(): LotteryHeaderState {
    val lifecycleTone = when (session_status) {
        "rolling" -> LotteryChipTone.Primary
        "result" -> LotteryChipTone.Warning
        "completed" -> LotteryChipTone.Neutral
        "ready", "collecting", "idle" -> LotteryChipTone.Warning
        else -> LotteryChipTone.Neutral
    }
    val attendeeTone = when {
        joined -> LotteryChipTone.Success
        self_service_open -> LotteryChipTone.Warning
        else -> LotteryChipTone.Neutral
    }

    return LotteryHeaderState(
        lifecycleLabel = statusLabel(),
        lifecycleTone = lifecycleTone,
        attendeeLabel = if (joined) "已参与" else "未参与",
        attendeeTone = attendeeTone,
        supportingText = roundSummaryLine()
    )
}

fun LotterySession.actionState(): LotteryActionState {
    return when {
        all_rounds_finished || session_status == "completed" -> LotteryActionState(
            message = if (joined) "抽签已结束，你已参与本场抽签，可继续查看结果记录。" else "抽签已结束，你未参与本场抽签，可继续查看结果记录。",
            tone = if (joined) LotteryChipTone.Success else LotteryChipTone.Neutral
        )
        session_status == "rolling" -> LotteryActionState(
            message = if (joined) "抽签进行中，你已在抽签池内，当前不能退出。" else "抽签进行中，当前不能再加入抽签池。",
            tone = LotteryChipTone.Primary
        )
        currentResultRound() != null -> LotteryActionState(
            message = if (self_service_open) "本轮结果展示中，如未开始下一轮，仍可在开抽前调整是否参与。" else if (joined) "本轮结果展示中，你已参与本场抽签，等待后续轮次或最终结果。" else "本轮结果展示中，当前不能再加入本场抽签。",
            tone = if (joined && !self_service_open) LotteryChipTone.Success else LotteryChipTone.Warning
        )
        displayRound() == null -> LotteryActionState(
            message = "暂未设置可抽取轮次，请等待主持人开始抽签。",
            tone = LotteryChipTone.Neutral
        )
        self_service_open && joined -> LotteryActionState(
            message = "抽签尚未开始，你已加入抽签池，如需调整可在开始前退出。",
            tone = LotteryChipTone.Success,
            secondaryLabel = "退出抽签池",
            secondaryEnabled = true
        )
        self_service_open && !joined -> LotteryActionState(
            message = "抽签尚未开始，可决定是否加入抽签池。",
            tone = LotteryChipTone.Warning,
            primaryLabel = "加入抽签池",
            primaryEnabled = true
        )
        joined -> LotteryActionState(
            message = "抽签已开始，你已参与本场抽签，当前仅可查看状态与结果。",
            tone = LotteryChipTone.Success
        )
        else -> LotteryActionState(
            message = "抽签已开始，当前不能再加入抽签池。",
            tone = LotteryChipTone.Neutral
        )
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

fun LotteryRound.roundStatusTone(session: LotterySession? = null): LotteryChipTone {
    return when (roundStatusLabel(session)) {
        "当前抽取" -> LotteryChipTone.Primary
        "当前结果" -> LotteryChipTone.Warning
        "下一轮" -> LotteryChipTone.Success
        "已抽取" -> LotteryChipTone.Neutral
        else -> LotteryChipTone.Neutral
    }
}

fun LotteryRound.metaSummary(session: LotterySession? = null): String {
    val statusText = roundStatusLabel(session)
    val repeatText = if (allow_repeat) "允许重复" else "不重复"
    return "$statusText · 抽取 $count 人 · $repeatText"
}

fun LotteryRound.winnerNamesSummary(): String {
    if (winners.isEmpty()) return ""
    return "中签名单：${winners.joinToString(separator = "、") { it.displayName() }}"
}

fun LotteryWinner.displayName(): String = name ?: user_name ?: "未知用户"
