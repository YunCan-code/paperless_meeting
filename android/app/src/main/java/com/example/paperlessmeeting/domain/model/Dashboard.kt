package com.example.paperlessmeeting.domain.model

import com.google.gson.annotations.SerializedName
import androidx.annotation.Keep

// ===== Dashboard Stats =====
@Keep
data class DashboardStats(
    @SerializedName("meeting_count") val meetingCount: Int = 0,
    @SerializedName("checkin_count") val checkinCount: Int = 0,
    @SerializedName("type_count") val typeCount: Int = 0,
    @SerializedName("reading_count") val readingCount: Int = 0,
    @SerializedName("total_duration_minutes") val totalDurationMinutes: Int = 0,
    val range: String = "month"
)

// ===== Check-In =====
@Keep
data class CheckInRequest(
    @SerializedName("user_id") val userId: Int,
    @SerializedName("meeting_id") val meetingId: Int,
    @SerializedName("duration_minutes") val durationMinutes: Int? = null
)

@Keep
data class MakeupRequest(
    @SerializedName("user_id") val userId: Int,
    @SerializedName("meeting_id") val meetingId: Int,
    @SerializedName("duration_minutes") val durationMinutes: Int? = null,
    val remark: String? = null
)

@Keep
data class CheckInResponse(
    val id: Int,
    @SerializedName("user_id") val userId: Int,
    @SerializedName("meeting_id") val meetingId: Int,
    @SerializedName("check_in_time") val checkInTime: String,
    @SerializedName("duration_minutes") val durationMinutes: Int?,
    @SerializedName("is_makeup") val isMakeup: Boolean,
    val remark: String?,
    @SerializedName("meeting_title") val meetingTitle: String?
)

// ===== Today Status =====
@Keep
data class TodayMeetingStatus(
    @SerializedName("meeting_id") val meetingId: Int,
    @SerializedName("meeting_title") val meetingTitle: String,
    @SerializedName("start_time") val startTime: String,
    @SerializedName("checked_in") val checkedIn: Boolean,
    @SerializedName("checkin_id") val checkinId: Int?
)

@Keep
data class TodayStatusResponse(
    @SerializedName("today_meetings") val todayMeetings: List<TodayMeetingStatus>,
    @SerializedName("checked_count") val checkedCount: Int,
    @SerializedName("total_count") val totalCount: Int
)

// ===== Collaborators =====
@Keep
data class Collaborator(
    @SerializedName("user_id") val userId: Int,
    val name: String,
    @SerializedName("co_meetings") val coMeetings: Int
)

@Keep
data class CollaboratorsResponse(
    val collaborators: List<Collaborator>
)

// ===== Type Distribution =====
@Keep
data class TypeDistributionItem(
    @SerializedName("type_name") val typeName: String,
    val count: Int
)

@Keep
data class TypeDistributionResponse(
    val distribution: List<TypeDistributionItem>
)

// ===== Heatmap =====
@Keep
data class HeatmapResponse(
    val heatmap: Map<String, Int>
)

// ===== Check-In History =====
@Keep
data class CheckInHistoryItem(
    val id: Int,
    @SerializedName("meeting_id") val meetingId: Int,
    @SerializedName("meeting_title") val meetingTitle: String,
    @SerializedName("check_in_time") val checkInTime: String,
    @SerializedName("duration_minutes") val durationMinutes: Int?,
    @SerializedName("is_makeup") val isMakeup: Boolean,
    val remark: String?
)
