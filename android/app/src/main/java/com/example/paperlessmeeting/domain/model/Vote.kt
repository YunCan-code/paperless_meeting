package com.example.paperlessmeeting.domain.model

/**
 * 投票主体
 */
data class Vote(
    val id: Int,
    val meeting_id: Int,
    val title: String,
    val description: String?,
    val is_multiple: Boolean,
    val is_anonymous: Boolean,
    val max_selections: Int,
    val duration_seconds: Int,
    val status: String, // draft, active, closed
    val started_at: String?,
    val created_at: String,
    val options: List<VoteOption>,
    val remaining_seconds: Int?, // 剩余时间（服务端计算）
    val wait_seconds: Int? = null, // 倒计时等待时间
    val user_voted: Boolean = false // 当前用户是否已投票
)

/**
 * 投票选项
 */
data class VoteOption(
    val id: Int,
    val content: String,
    val sort_order: Int = 0,
    val vote_count: Int? = null,  // 结果时返回
    val percent: Float? = null     // 百分比
)

/**
 * 投票提交请求
 */
data class VoteSubmitRequest(
    val user_id: Int,
    val option_ids: List<Int>
)

/**
 * 投票结果
 */
data class VoteResult(
    val vote_id: Int,
    val title: String,
    val total_voters: Int,
    val results: List<VoteOptionResult>
)

data class VoteOptionResult(
    val option_id: Int,
    val content: String,
    val count: Int,
    val percent: Float
)
