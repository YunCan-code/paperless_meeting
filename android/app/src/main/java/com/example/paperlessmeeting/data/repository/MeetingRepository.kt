package com.example.paperlessmeeting.data.repository

import com.example.paperlessmeeting.data.remote.ApiService
import com.example.paperlessmeeting.domain.model.Meeting
import javax.inject.Inject
import javax.inject.Singleton

interface MeetingRepository {
    suspend fun getMeetings(): List<Meeting>
    suspend fun getMeetingById(id: Int): Meeting?
    suspend fun downloadFile(url: String, destFile: java.io.File): Boolean
}

@Singleton
class MeetingRepositoryImpl @Inject constructor(
    private val api: ApiService
) : MeetingRepository {
    override suspend fun getMeetings(): List<Meeting> {
        return api.getMeetings()
    }

    override suspend fun getMeetingById(id: Int): Meeting? {
        return try {
            api.getMeeting(id)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun downloadFile(url: String, destFile: java.io.File): Boolean {
        return try {
            val response = api.downloadFile(url)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                response.byteStream().use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
