package com.example.paperlessmeeting.data.remote.model

import com.example.paperlessmeeting.domain.model.LotteryHistoryResponse
import com.example.paperlessmeeting.domain.model.LotteryParticipant
import com.example.paperlessmeeting.domain.model.LotteryRound
import com.example.paperlessmeeting.domain.model.LotterySession
import com.example.paperlessmeeting.domain.model.LotteryWinner

data class LotteryWinnerPayload(
    val id: Int? = null,
    val user_id: Int? = null,
    val user_name: String? = null,
    val name: String? = null,
    val department: String? = null,
    val avatar: String? = null,
    val winning_at: String? = null
)

data class LotteryRoundPayload(
    val id: Int,
    val title: String,
    val count: Int = 1,
    val allow_repeat: Boolean = false,
    val sort_order: Int? = null,
    val status: String = "draft",
    val created_at: String? = null,
    val winners: List<LotteryWinnerPayload>? = null
)

data class LotteryHistoryResponsePayload(
    val meeting_id: Int = 0,
    val meeting_title: String = "",
    val rounds: List<LotteryRoundPayload>? = null
)

data class LotteryParticipantPayload(
    val id: Int? = null,
    val user_id: Int? = null,
    val name: String? = null,
    val avatar: String? = null,
    val department: String? = null,
    val status: String? = null,
    val is_winner: Boolean = false,
    val winning_lottery_id: Int? = null,
    val created_at: String? = null
)

data class LotterySessionPayload(
    val meeting_id: Int = 0,
    val session_status: String? = null,
    val current_round_id: Int? = null,
    val current_round: LotteryRoundPayload? = null,
    val next_round_id: Int? = null,
    val next_round: LotteryRoundPayload? = null,
    val participants: List<LotteryParticipantPayload>? = null,
    val participants_count: Int? = null,
    val winners: List<LotteryWinnerPayload>? = null,
    val joined: Boolean? = null,
    val all_rounds_finished: Boolean? = null,
    val rounds: List<LotteryRoundPayload>? = null
)

fun LotteryWinnerPayload.toDomain(): LotteryWinner {
    return LotteryWinner(
        id = id ?: user_id ?: 0,
        user_id = user_id,
        user_name = user_name,
        name = name,
        department = department,
        avatar = avatar,
        winning_at = winning_at
    )
}

fun LotteryRoundPayload.toDomain(): LotteryRound {
    return LotteryRound(
        id = id,
        title = title,
        count = count,
        allow_repeat = allow_repeat,
        sort_order = sort_order ?: 0,
        status = status,
        created_at = created_at,
        winners = winners?.map { it.toDomain() } ?: emptyList()
    )
}

fun LotteryHistoryResponsePayload.toDomain(): LotteryHistoryResponse {
    return LotteryHistoryResponse(
        meeting_id = meeting_id,
        meeting_title = meeting_title,
        rounds = rounds?.map { it.toDomain() } ?: emptyList()
    )
}

fun LotteryParticipantPayload.toDomain(): LotteryParticipant {
    val resolvedId = user_id ?: id ?: 0
    return LotteryParticipant(
        id = resolvedId,
        user_id = resolvedId,
        name = name.orEmpty(),
        avatar = avatar,
        department = department,
        status = status ?: "joined",
        is_winner = is_winner,
        winning_lottery_id = winning_lottery_id,
        created_at = created_at
    )
}

fun LotterySessionPayload.toDomain(): LotterySession {
    val roundList = rounds?.map { it.toDomain() } ?: emptyList()
    val participantList = participants?.map { it.toDomain() } ?: emptyList()
    val resolvedCurrentRound = current_round?.toDomain() ?: roundList.firstOrNull { it.id == current_round_id }
    val resolvedNextRound = next_round?.toDomain() ?: roundList.firstOrNull { it.id == next_round_id }
    return LotterySession(
        meeting_id = meeting_id,
        session_status = session_status ?: "idle",
        current_round_id = current_round_id,
        current_round = resolvedCurrentRound,
        next_round_id = next_round_id,
        next_round = resolvedNextRound,
        participants = participantList,
        participants_count = participants_count ?: participantList.size,
        winners = winners?.map { it.toDomain() } ?: emptyList(),
        joined = joined ?: false,
        all_rounds_finished = all_rounds_finished ?: false,
        rounds = roundList
    )
}
