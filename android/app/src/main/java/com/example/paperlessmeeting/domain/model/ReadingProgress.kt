package com.example.paperlessmeeting.domain.model

import com.google.gson.annotations.SerializedName
import androidx.annotation.Keep

@Keep
data class ReadingProgressRequest(
    @SerializedName("user_id") val userId: Int,
    @SerializedName("file_url") val fileUrl: String,
    @SerializedName("file_name") val fileName: String,
    @SerializedName("current_page") val currentPage: Int,
    @SerializedName("total_pages") val totalPages: Int
)

@Keep
data class ReadingProgressResponse(
    @SerializedName("file_url") val fileUrl: String,
    @SerializedName("file_name") val fileName: String,
    @SerializedName("current_page") val currentPage: Int,
    @SerializedName("total_pages") val totalPages: Int,
    @SerializedName("updated_at") val updatedAt: String
)
