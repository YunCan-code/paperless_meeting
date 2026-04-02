package com.example.paperlessmeeting.domain.model

data class LotteryParticipantActionRequest(
    val user_id: Int
)

data class LotteryWinner(
    val id: Int = 0,
    val user_id: Int? = null,
    val user_name: String? = null,
    val name: String? = null,
    val department: String? = null,
    val avatar: String? = null,
    val winning_at: String? = null
)

data class LotteryRound(
    val id: Int,
    val title: String,
    val count: Int = 1,
    val allow_repeat: Boolean = false,
    val sort_order: Int = 0,
    val status: String = "draft",
    val created_at: String? = null,
    val winners: List<LotteryWinner> = emptyList()
)

data class LotteryHistoryResponse(
    val meeting_id: Int = 0,
    val meeting_title: String = "",
    val rounds: List<LotteryRound> = emptyList()
)

data class LotteryParticipant(
    val id: Int = 0,
    val user_id: Int = id,
    val name: String = "",
    val avatar: String? = null,
    val department: String? = null,
    val status: String = "joined",
    val is_winner: Boolean = false,
    val winning_lottery_id: Int? = null,
    val created_at: String? = null
)

data class LotterySession(
    val meeting_id: Int = 0,
    val session_status: String = "idle",
    val self_service_open: Boolean = true,
    val current_round_id: Int? = null,
    val current_round: LotteryRound? = null,
    val next_round_id: Int? = null,
    val next_round: LotteryRound? = null,
    val participants: List<LotteryParticipant> = emptyList(),
    val participants_count: Int = 0,
    val winners: List<LotteryWinner> = emptyList(),
    val joined: Boolean = false,
    val all_rounds_finished: Boolean = false,
    val rounds: List<LotteryRound> = emptyList()
)
