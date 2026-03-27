package com.example.paperlessmeeting.data.local

import android.content.Context
import com.example.paperlessmeeting.data.remote.ApiService
import com.example.paperlessmeeting.domain.model.DeleteReadingProgressRequest
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
    private val KEY_DELETED_PROGRESS_IDS = "deleted_progress_ids"
    private val MAX_RECENT_PROGRESS_ITEMS = 20

    suspend fun saveProgress(uniqueId: String, fileName: String, page: Int, total: Int, localPath: String? = null) {
        withContext(Dispatchers.IO) {
            removeDeletedMarker(uniqueId)

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
            
            val trimmedList = currentList.take(MAX_RECENT_PROGRESS_ITEMS)
            persistProgressList(trimmedList)

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

    suspend fun deleteProgress(uniqueId: String) {
        withContext(Dispatchers.IO) {
            val updatedList = getAllProgressLocal().filterNot { it.uniqueId == uniqueId }
            addDeletedMarker(uniqueId)
            persistProgressList(updatedList)

            val userId = userPreferences.getUserId()
            if (userId != -1) {
                try {
                    apiService.deleteReadingProgress(userId, uniqueId)
                } catch (e: retrofit2.HttpException) {
                    if (e.code() == 405) {
                        try {
                            apiService.deleteReadingProgressCompat(
                                DeleteReadingProgressRequest(
                                    userId = userId,
                                    fileUrl = uniqueId
                                )
                            )
                        } catch (fallbackException: Exception) {
                            android.util.Log.w("ReadingProgress", "deleteProgress: compat delete failed for $uniqueId", fallbackException)
                        }
                    } else {
                        android.util.Log.w("ReadingProgress", "deleteProgress: server delete failed for $uniqueId", e)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("ReadingProgress", "deleteProgress: server delete failed for $uniqueId", e)
                }
            }
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
                val deletedIds = getDeletedMarkers()
                android.util.Log.d("ReadingProgress", "loadFromServer: serverList.size=${serverList.size}")
                for (item in serverList) {
                    android.util.Log.d("ReadingProgress", "  server item: url=${item.fileUrl}, name=${item.fileName}, page=${item.currentPage}")
                }

                val currentLocalMap = getAllProgressLocal().associateBy { it.uniqueId }

                val mergedList = serverList
                    .filterNot { it.fileUrl in deletedIds }
                    .sortedByDescending { parseUpdatedAtMillis(it.updatedAt) }
                    .take(MAX_RECENT_PROGRESS_ITEMS)
                    .map { item ->
                        val existingLocal = currentLocalMap[item.fileUrl]
                        val localPath = existingLocal?.localPath
                            ?: findCachedLocalPath(item.fileUrl, item.fileName)
                        ReadingProgress(
                            uniqueId = item.fileUrl,
                            fileName = item.fileName,
                            currentPage = item.currentPage,
                            totalPages = item.totalPages,
                            lastReadTime = parseUpdatedAtMillis(item.updatedAt),
                            localPath = localPath
                        )
                    }

                android.util.Log.d("ReadingProgress", "loadFromServer: mergedList.size=${mergedList.size}")
                persistProgressList(mergedList)
            } catch (e: Exception) {
                android.util.Log.e("ReadingProgress", "loadFromServer FAILED", e)
                e.printStackTrace()
            }
        }
    }

    private fun parseUpdatedAtMillis(updatedAt: String): Long {
        return try {
            java.time.OffsetDateTime.parse(updatedAt).toInstant().toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    }

    private fun findCachedLocalPath(fileUrl: String, fileName: String): String? {
        return try {
            val cacheDir = context.cacheDir
            val hashPrefix = fileUrl.hashCode().toString()
            val extension = fileName.substringAfterLast(".", "pdf")

            val preferredFile = java.io.File(cacheDir, "$hashPrefix.$extension")
            if (preferredFile.exists()) {
                return preferredFile.absolutePath
            }

            val fallbackPdfFile = java.io.File(cacheDir, "$hashPrefix.pdf")
            if (fallbackPdfFile.exists()) {
                return fallbackPdfFile.absolutePath
            }

            cacheDir.listFiles()
                ?.firstOrNull { it.isFile && it.name.startsWith("$hashPrefix.") }
                ?.absolutePath
        } catch (_: Exception) {
            null
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
            prefs.edit()
                .remove(KEY_PROGRESS_LIST)
                .remove(KEY_DELETED_PROGRESS_IDS)
                .apply()
        }
    }

    private fun persistProgressList(progressList: List<ReadingProgress>) {
        val json = gson.toJson(progressList)
        prefs.edit().putString(KEY_PROGRESS_LIST, json).apply()
    }

    private fun getDeletedMarkers(): Set<String> {
        return prefs.getStringSet(KEY_DELETED_PROGRESS_IDS, emptySet()).orEmpty().toSet()
    }

    private fun addDeletedMarker(uniqueId: String) {
        val updated = getDeletedMarkers().toMutableSet()
        updated.add(uniqueId)
        prefs.edit().putStringSet(KEY_DELETED_PROGRESS_IDS, updated).apply()
    }

    private fun removeDeletedMarker(uniqueId: String) {
        val updated = getDeletedMarkers().toMutableSet()
        if (updated.remove(uniqueId)) {
            prefs.edit().putStringSet(KEY_DELETED_PROGRESS_IDS, updated).apply()
        }
    }
}






