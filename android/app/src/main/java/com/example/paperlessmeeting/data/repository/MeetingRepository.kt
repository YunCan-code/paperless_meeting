package com.example.paperlessmeeting.data.repository

import com.example.paperlessmeeting.data.remote.ApiService
import com.example.paperlessmeeting.domain.model.Meeting
import javax.inject.Inject
import javax.inject.Singleton

interface MeetingRepository {
    suspend fun getMeetings(
        skip: Int = 0, 
        limit: Int = 20,
        sort: String? = "desc",
        startDate: String? = null,
        endDate: String? = null
    ): List<Meeting>
    suspend fun getMeetingById(id: Int): Meeting?
    suspend fun downloadFile(url: String, destFile: java.io.File): Boolean
    suspend fun downloadFileWithProgress(
        url: String, 
        destFile: java.io.File,
        onProgress: (Float) -> Unit
    ): Boolean

    suspend fun getSyncState(meetingId: Int): com.example.paperlessmeeting.domain.model.MeetingSyncState?
    suspend fun updateSyncState(meetingId: Int, fileId: Int, pageNumber: Int, isSyncing: Boolean, fileUrl: String?): com.example.paperlessmeeting.domain.model.MeetingSyncState?

    // Vote methods
    suspend fun getActiveVote(meetingId: Int): com.example.paperlessmeeting.domain.model.Vote?
    suspend fun getVoteList(meetingId: Int): List<com.example.paperlessmeeting.domain.model.Vote>
    suspend fun getVote(voteId: Int, userId: Int? = null): com.example.paperlessmeeting.domain.model.Vote?
    suspend fun submitVote(voteId: Int, userId: Int, optionIds: List<Int>)
    suspend fun getVoteResult(voteId: Int): com.example.paperlessmeeting.domain.model.VoteResult?
    suspend fun getLotteryHistory(meetingId: Int): com.example.paperlessmeeting.domain.model.LotteryHistoryResponse?
    suspend fun getVoteHistory(userId: Int, skip: Int = 0, limit: Int = 20): List<com.example.paperlessmeeting.domain.model.Vote>
}

@Singleton
class MeetingRepositoryImpl @Inject constructor(
    private val api: ApiService
) : MeetingRepository {
    override suspend fun getMeetings(
        skip: Int, 
        limit: Int,
        sort: String?,
        startDate: String?,
        endDate: String?
    ): List<Meeting> {
        return api.getMeetings(skip, limit, sort, startDate, endDate)
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

    override suspend fun downloadFileWithProgress(
        url: String, 
        destFile: java.io.File,
        onProgress: (Float) -> Unit
    ): Boolean {
        return try {
            val response = api.downloadFile(url)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val contentLength = response.contentLength()
                var bytesWritten = 0L
                
                response.byteStream().use { input ->
                    destFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            bytesWritten += bytesRead
                            
                            // 计算进度 (如果 contentLength 未知则使用 -1)
                            val progress = if (contentLength > 0) {
                                (bytesWritten.toFloat() / contentLength).coerceIn(0f, 1f)
                            } else {
                                -1f // 表示进度未知
                            }
                            onProgress(progress)
                        }
                    }
                }
            }
            onProgress(1f) // 确保完成时是100%
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun getSyncState(meetingId: Int): com.example.paperlessmeeting.domain.model.MeetingSyncState? {
        return try {
            api.getSyncState(meetingId)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun updateSyncState(meetingId: Int, fileId: Int, pageNumber: Int, isSyncing: Boolean, fileUrl: String?): com.example.paperlessmeeting.domain.model.MeetingSyncState? {
        return try {
            api.updateSyncState(meetingId, fileId, pageNumber, isSyncing, fileUrl)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun getActiveVote(meetingId: Int): com.example.paperlessmeeting.domain.model.Vote? {
        return try {
            api.getActiveVote(meetingId)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun getVoteList(meetingId: Int): List<com.example.paperlessmeeting.domain.model.Vote> {
        return try {
            api.getVoteList(meetingId)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun getVote(voteId: Int, userId: Int?): com.example.paperlessmeeting.domain.model.Vote? {
        return try {
            api.getVote(voteId, userId)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun submitVote(voteId: Int, userId: Int, optionIds: List<Int>) {
        try {
            val request = com.example.paperlessmeeting.domain.model.VoteSubmitRequest(userId, optionIds)
            api.submitVote(voteId, request)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    override suspend fun getVoteResult(voteId: Int): com.example.paperlessmeeting.domain.model.VoteResult? {
        return try {
            api.getVoteResult(voteId)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun getLotteryHistory(meetingId: Int): com.example.paperlessmeeting.domain.model.LotteryHistoryResponse? {
        return try {
            api.getLotteryHistory(meetingId)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun getVoteHistory(userId: Int, skip: Int, limit: Int): List<com.example.paperlessmeeting.domain.model.Vote> {
        return try {
            api.getVoteHistory(userId, skip, limit)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
