package com.example.paperlessmeeting.data.local

import android.content.Context
import com.example.paperlessmeeting.data.remote.ApiService
import com.example.paperlessmeeting.domain.model.ReadingProgressRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

import androidx.annotation.Keep

@Keep
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
    @ApplicationContext private val context: Context,
    private val apiService: ApiService,
    private val userPreferences: UserPreferences
) {
    private val prefs = context.getSharedPreferences("reading_progress_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val KEY_PROGRESS_LIST = "progress_list"

    suspend fun saveProgress(uniqueId: String, fileName: String, page: Int, total: Int, localPath: String? = null) {
        withContext(Dispatchers.IO) {
            // 保存到本地
            val currentList = getAllProgressLocal().toMutableList()
            currentList.removeAll { it.uniqueId == uniqueId }
            
            val newEntry = ReadingProgress(
                uniqueId = uniqueId,
                fileName = fileName,
                currentPage = page,
                totalPages = total,
                lastReadTime = System.currentTimeMillis(),
                localPath = localPath
            )
            currentList.add(0, newEntry)
            
            val trimmedList = if (currentList.size > 20) currentList.take(20) else currentList
            val json = gson.toJson(trimmedList)
            prefs.edit().putString(KEY_PROGRESS_LIST, json).apply()

            // 同步到服务端
            val userId = userPreferences.getUserId()
            if (userId != -1) {
                try {
                    apiService.saveReadingProgress(
                        ReadingProgressRequest(
                            userId = userId,
                            fileUrl = uniqueId,
                            fileName = fileName,
                            currentPage = page,
                            totalPages = total
                        )
                    )
                } catch (e: Exception) {
                    // 网络失败时静默忽略，本地已保存
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun getProgress(uniqueId: String): ReadingProgress? {
        return withContext(Dispatchers.IO) {
            getAllProgressLocal().find { it.uniqueId == uniqueId }
        }
    }

    /**
     * 从服务端拉取当前用户的阅读进度并写入本地
     */
    suspend fun loadFromServer() {
        withContext(Dispatchers.IO) {
            val userId = userPreferences.getUserId()
            android.util.Log.d("ReadingProgress", "loadFromServer: userId=$userId")
            if (userId == -1) return@withContext

            try {
                val serverList = apiService.getReadingProgress(userId)
                android.util.Log.d("ReadingProgress", "loadFromServer: serverList.size=${serverList.size}")
                for (item in serverList) {
                    android.util.Log.d("ReadingProgress", "  server item: url=${item.fileUrl}, name=${item.fileName}, page=${item.currentPage}")
                }

                val currentLocalMap = getAllProgressLocal().associateBy { it.uniqueId }

                val mergedList = serverList.map { item ->
                    // 尝试从本地找，如果本地有缓存，保留它的 localPath
                    val existingLocal = currentLocalMap[item.fileUrl]
                    ReadingProgress(
                        uniqueId = item.fileUrl,
                        fileName = item.fileName,
                        currentPage = item.currentPage,
                        totalPages = item.totalPages,
                        lastReadTime = System.currentTimeMillis(),
                        localPath = existingLocal?.localPath // 保留本地路径
                    )
                }
                android.util.Log.d("ReadingProgress", "loadFromServer: mergedList.size=${mergedList.size}")
                val json = gson.toJson(mergedList)
                prefs.edit().putString(KEY_PROGRESS_LIST, json).apply()
            } catch (e: Exception) {
                android.util.Log.e("ReadingProgress", "loadFromServer FAILED", e)
                e.printStackTrace()
            }
        }
    }

    suspend fun getAllProgress(): List<ReadingProgress> {
        return getAllProgressLocal()
    }

    private suspend fun getAllProgressLocal(): List<ReadingProgress> {
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

    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            prefs.edit().remove(KEY_PROGRESS_LIST).apply()
        }
    }
}
