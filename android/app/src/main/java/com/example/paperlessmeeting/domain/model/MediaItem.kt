package com.example.paperlessmeeting.domain.model

import com.google.gson.annotations.SerializedName

data class MediaItem(
    val id: Int,
    val kind: String,
    val title: String,
    @SerializedName("parent_id")
    val parentId: Int?,
    val extension: String?,
    @SerializedName("file_size")
    val fileSize: Int = 0,
    @SerializedName("visible_on_android")
    val visibleOnAndroid: Boolean = true,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("updated_at")
    val updatedAt: String? = null,
    val size: String? = null,
    val previewUrl: String? = null,
    val thumbnailUrl: String? = null,
    @SerializedName("children_count")
    val childrenCount: Int = 0
)

data class MediaBreadcrumb(
    val id: Int,
    val title: String
)
