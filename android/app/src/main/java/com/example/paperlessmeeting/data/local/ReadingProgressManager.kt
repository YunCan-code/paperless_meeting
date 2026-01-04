package com.example.paperlessmeeting.data.local

import android.content.Context
import com.example.paperlessmeeting.domain.model.Meeting
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ReadingProgress(
    val uniqueId: String, // Typically the URL or a combination
    val fileName: String,
    val currentPage: Int, // 0-indexed
    val totalPages: Int,
    val lastReadTime: Long,
    val localPath: String? = null
)

@Singleton
class ReadingProgressManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("reading_progress_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val KEY_PROGRESS_LIST = "progress_list"

    suspend fun saveProgress(uniqueId: String, fileName: String, page: Int, total: Int, localPath: String? = null) {
        withContext(Dispatchers.IO) {
            val currentList = getAllProgress().toMutableList()
            // Remove existing entry for this file if any
            currentList.removeAll { it.uniqueId == uniqueId }
            
            // Add new entry
            val newEntry = ReadingProgress(
                uniqueId = uniqueId,
                fileName = fileName,
                currentPage = page,
                totalPages = total,
                lastReadTime = System.currentTimeMillis(),
                localPath = localPath
            )
            // Add to index 0
            currentList.add(0, newEntry)
            
            // Limit list size (e.g. keep last 20)
            val trimmedList = if (currentList.size > 20) currentList.take(20) else currentList
            
            val json = gson.toJson(trimmedList)
            prefs.edit().putString(KEY_PROGRESS_LIST, json).apply()
        }
    }

    suspend fun getProgress(uniqueId: String): ReadingProgress? {
        return withContext(Dispatchers.IO) {
            getAllProgress().find { it.uniqueId == uniqueId }
        }
    }

    suspend fun getAllProgress(): List<ReadingProgress> {
        return withContext(Dispatchers.IO) {
            val json = prefs.getString(KEY_PROGRESS_LIST, null)
            if (json.isNullOrEmpty()) {
                emptyList()
            } else {
                try {
                    val type = object : TypeToken<List<ReadingProgress>>() {}.type
                    gson.fromJson(json, type)
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }
    }
}
