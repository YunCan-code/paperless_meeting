package com.example.paperlessmeeting.domain.model

data class StartLotteryRequest(
    val meetingId: Int,
    val count: Int
)

data class LotteryWinner(
    val id: Int,
    val user_name: String,
    val winning_at: String
)

data class LotteryRound(
    val id: Int,
    val title: String,
    val count: Int,
    val status: String, // pending, active, finished
    val winners: List<LotteryWinner>
)

data class LotteryHistoryResponse(
    val meeting_id: Int,
    val meeting_title: String,
    val rounds: List<LotteryRound>
)

// Socket State Models
data class LotteryState(
    val status: String,
    val participants: List<LotteryParticipant>? = null,
    val current_title: String? = null,
    val current_count: Int = 1,
    val winners: List<LotteryWinnerMap>? = null,
    val participant_count: Int = 0
)

data class LotteryParticipant(
    val id: Any, // Can be int or string
    val name: String,
    val sid: String?,
    val avatar: String?,
    val department: String?
)

data class LotteryWinnerMap(
    val id: Any, // ID from map might be different format
    val name: String
)
