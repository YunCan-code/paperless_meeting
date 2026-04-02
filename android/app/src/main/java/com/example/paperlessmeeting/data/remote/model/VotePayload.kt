package com.example.paperlessmeeting.data.remote.model

import com.example.paperlessmeeting.domain.model.Vote
import com.example.paperlessmeeting.domain.model.VoteOption
import com.example.paperlessmeeting.domain.model.VoteOptionResult
import com.example.paperlessmeeting.domain.model.VoteResult

data class VotePayload(
    val id: Int,
    val meeting_id: Int,
    val title: String,
    val description: String? = null,
    val is_multiple: Boolean = false,
    val is_anonymous: Boolean = false,
    val max_selections: Int = 1,
    val duration_seconds: Int = 0,
    val status: String = "draft",
    val started_at: String? = null,
    val created_at: String = "",
    val options: List<VoteOptionPayload>? = null,
    val remaining_seconds: Int? = null,
    val wait_seconds: Int? = null,
    val selected_option_ids: List<Int>? = null,
    val user_voted: Boolean = false
)

data class VoteOptionPayload(
    val id: Int,
    val content: String,
    val sort_order: Int = 0,
    val vote_count: Int? = null,
    val percent: Float? = null
)

data class VoteResultPayload(
    val vote_id: Int,
    val title: String,
    val total_voters: Int = 0,
    val results: List<VoteOptionResultPayload>? = null
)

data class VoteOptionResultPayload(
    val option_id: Int,
    val content: String,
    val count: Int = 0,
    val percent: Float = 0f,
    val voters: List<String>? = null
)

fun VotePayload.toDomain(): Vote {
    return Vote(
        id = id,
        meeting_id = meeting_id,
        title = title,
        description = description,
        is_multiple = is_multiple,
        is_anonymous = is_anonymous,
        max_selections = max_selections,
        duration_seconds = duration_seconds,
        status = status,
        started_at = started_at,
        created_at = created_at,
        options = options?.map { it.toDomain() } ?: emptyList(),
        remaining_seconds = remaining_seconds,
        wait_seconds = wait_seconds,
        selected_option_ids = selected_option_ids ?: emptyList(),
        user_voted = user_voted
    )
}

fun VoteOptionPayload.toDomain(): VoteOption {
    return VoteOption(
        id = id,
        content = content,
        sort_order = sort_order,
        vote_count = vote_count,
        percent = percent
    )
}

fun VoteResultPayload.toDomain(): VoteResult {
    return VoteResult(
        vote_id = vote_id,
        title = title,
        total_voters = total_voters,
        results = results?.map { it.toDomain() } ?: emptyList()
    )
}

fun VoteOptionResultPayload.toDomain(): VoteOptionResult {
    return VoteOptionResult(
        option_id = option_id,
        content = content,
        count = count,
        percent = percent,
        voters = voters ?: emptyList()
    )
}
