package com.example.paperlessmeeting.domain.model

import androidx.compose.ui.graphics.Color
import java.time.LocalDateTime

enum class MeetingType(val displayName: String, val color: Color) {
    Weekly("周例会", Color(0xFF4CAF50)),       // Green for routine
    Urgent("紧急会议", Color(0xFFF44336)),      // Red for urgent
    Review("评审会", Color(0xFFFF9800)),      // Orange for review
    Kickoff("启动会", Color(0xFF2196F3)),     // Blue for start
    General("普通会议", Color(0xFF9E9E9E))      // Grey for others
}

enum class MeetingStatus(val displayName: String) {
    Upcoming("即将开始"),
    Ongoing("进行中"),
    Finished("已结束"),
    Draft("草稿")
}

data class Meeting(
    val id: String,
    val title: String,
    val type: MeetingType,
    val status: MeetingStatus,
    val startTime: String, // Simplified for UI prototyping
    val endTime: String,
    val location: String,
    val host: String,
    val imageUrl: String? = null // For visuals
)
