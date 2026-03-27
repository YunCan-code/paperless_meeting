package com.example.paperlessmeeting.utils

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

val APP_MEETING_ZONE_ID: ZoneId = ZoneId.of("Asia/Shanghai")

fun currentMeetingDate(): LocalDate = LocalDate.now(APP_MEETING_ZONE_ID)

fun currentMeetingDateTime(): LocalDateTime = LocalDateTime.now(APP_MEETING_ZONE_ID)

fun parseMeetingDateTime(value: String?): LocalDateTime? {
    if (value.isNullOrBlank()) return null
    val normalized = value.substringBefore(".").replace(" ", "T")
    return try {
        LocalDateTime.parse(normalized)
    } catch (_: Exception) {
        try {
            OffsetDateTime.parse(normalized).toLocalDateTime()
        } catch (_: Exception) {
            null
        }
    }
}
