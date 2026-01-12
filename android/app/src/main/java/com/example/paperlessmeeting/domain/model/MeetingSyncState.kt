package com.example.paperlessmeeting.domain.model

data class MeetingSyncState(
    val meeting_id: Int,
    val file_id: Int,
    val page_number: Int,
    val file_url: String? = null,
    val timestamp: Double,
    val is_syncing: Boolean
)

data class SyncStateRequest(
    val file_id: Int,
    val page_number: Int,
    val is_syncing: Boolean,
    val file_url: String? = null
)
