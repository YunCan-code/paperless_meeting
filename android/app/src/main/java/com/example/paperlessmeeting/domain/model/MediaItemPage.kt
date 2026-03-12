package com.example.paperlessmeeting.domain.model

data class MediaItemPage(
    val items: List<MediaItem>,
    val total: Int,
    val skip: Int,
    val limit: Int
)
