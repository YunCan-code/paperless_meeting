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

data class Attachment(
    val id: Int,
    @com.google.gson.annotations.SerializedName("display_name")
    val displayName: String,
    @com.google.gson.annotations.SerializedName("file_size")
    val fileSize: Int,
    @com.google.gson.annotations.SerializedName("sort_order")
    val sortOrder: Int,
    val filename: String
)

data class Meeting(
    val id: Int,
    val title: String,
    @com.google.gson.annotations.SerializedName("meeting_type_id")
    val meetingTypeId: Int,
    val status: String? = null, // Backend might not calculate this
    @com.google.gson.annotations.SerializedName("start_time")
    val startTime: String, 
    @com.google.gson.annotations.SerializedName("end_time")
    val endTime: String?,
    val location: String?,
    val host: String?,
    val description: String? = null,
    @com.google.gson.annotations.SerializedName("card_image_url")
    val cardImageUrl: String? = null,
    val attachments: List<Attachment>? = emptyList()
) {
    // Helper to map backend status string to UI Enum
    // Or calculate dynamically if backend status is missing
    fun getUiStatus(): MeetingStatus {
        // 1. Try backend status first if available
        when(status?.lowercase()) {
            "upcoming" -> return MeetingStatus.Upcoming
            "ongoing" -> return MeetingStatus.Ongoing
            "finished", "completed" -> return MeetingStatus.Finished
        }

        // 2. Fallback to time-based calculation
        return try {
            // Assume format "yyyy-MM-dd HH:mm:ss" or ISO. 
            // Backend in Python usually returns ISO "2023-12-19T13:00:00" or simple space.
            // Let's try ISO first then fallback.
            val now = java.time.LocalDateTime.now()
            // Fix: Handle potential time formats. For now assume ISO-like string from standard JSON.
            // Simple string compare is risky but let's try strict parsing.
            // Simplified logic: If we cant parse, return Draft.
            val formatter = java.time.format.DateTimeFormatter.ISO_DATE_TIME
            // Note: If backend sends "2023-12-19 13:00:00", replace space with T
            val cleanStart = startTime.replace(" ", "T")
            val start = java.time.LocalDateTime.parse(cleanStart)
            
            // Auto-calculate end time logic:
            // If explicit end time exists, use it. Otherwise assume 4 hours duration (consecutive meetings usually within a session).
            // Actually user said "no specific end time", so maybe just rely on "Show Latest Started".
            // But to avoid showing yesterday's meeting as "Ongoing", let's cap it at End of Day or 4 hours.
            // Let's us 4 hours as a safe default for a "session".
            val end = if (endTime != null && endTime.isNotEmpty()) {
                java.time.LocalDateTime.parse(endTime.replace(" ", "T"))
            } else {
                start.plusHours(4)
            }

            if (now.isAfter(end)) {
                MeetingStatus.Finished
            } else if (now.isAfter(start)) {
                 MeetingStatus.Ongoing 
            } else {
                 MeetingStatus.Upcoming
            }
        } catch (e: Exception) {
            MeetingStatus.Draft
        }
    }

    // Helper to map backend type ID to UI Enum
    fun getUiType(): MeetingType {
        // Simple mock mapping: ID % Enum.values().size
        val types = MeetingType.values()
        return types.getOrElse(meetingTypeId % types.size) { MeetingType.General }
    }
}
