package com.example.paperlessmeeting.data.repository

import com.example.paperlessmeeting.data.remote.ApiService
import com.example.paperlessmeeting.data.remote.model.toDomain
import com.example.paperlessmeeting.domain.model.Meeting
import com.example.paperlessmeeting.domain.model.CheckInResponse
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

interface MeetingRepository {
    suspend fun login(request: com.example.paperlessmeeting.domain.model.LoginRequest): com.example.paperlessmeeting.domain.model.LoginResponse
    suspend fun getSettings(): Map<String, String>
    suspend fun getMeetings(
        skip: Int = 0, 
        limit: Int = 20,
        sort: String? = "desc",
        startDate: String? = null,
        endDate: String? = null,
        userId: Int? = null
    ): List<Meeting>
    suspend fun getMeetingById(id: Int, userId: Int? = null): com.example.paperlessmeeting.utils.Resource<Meeting>
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
    suspend fun getUserLotteryHistory(userId: Int): List<com.example.paperlessmeeting.domain.model.LotteryHistoryResponse>
    suspend fun checkIn(userId: Int, meetingId: Int): CheckInResponse
    suspend fun cancelCheckIn(checkinId: Int, userId: Int)
}

@Singleton
class MeetingRepositoryImpl @Inject constructor(
    private val api: ApiService
) : MeetingRepository {

    private fun rethrowCancellation(throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
    }

    override suspend fun login(request: com.example.paperlessmeeting.domain.model.LoginRequest): com.example.paperlessmeeting.domain.model.LoginResponse {
        return api.login(request)
    }

    override suspend fun getSettings(): Map<String, String> {
        return api.getSettings()
    }

    override suspend fun getMeetings(
        skip: Int, 
        limit: Int,
        sort: String?,
        startDate: String?,
        endDate: String?,
        userId: Int?
    ): List<Meeting> {
        return api.getMeetings(skip, limit, sort, startDate, endDate, userId)
    }

    override suspend fun getMeetingById(id: Int, userId: Int?): com.example.paperlessmeeting.utils.Resource<Meeting> {
        return try {
            val meeting = api.getMeeting(id, userId)
            com.example.paperlessmeeting.utils.Resource.Success(meeting)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            e.printStackTrace()
            com.example.paperlessmeeting.utils.Resource.Error("HTTP_${e.code()}")
        } catch (e: Exception) {
            e.printStackTrace()
            com.example.paperlessmeeting.utils.Resource.Error(e.message ?: "Unknown Error")
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
            rethrowCancellation(e)
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
            rethrowCancellation(e)
            e.printStackTrace()
            false
        }
    }

    override suspend fun getSyncState(meetingId: Int): com.example.paperlessmeeting.domain.model.MeetingSyncState? {
        return try {
            api.getSyncState(meetingId)
        } catch (e: Exception) {
            rethrowCancellation(e)
            e.printStackTrace()
            null
        }
    }

    override suspend fun updateSyncState(meetingId: Int, fileId: Int, pageNumber: Int, isSyncing: Boolean, fileUrl: String?): com.example.paperlessmeeting.domain.model.MeetingSyncState? {
        return try {
            api.updateSyncState(meetingId, fileId, pageNumber, isSyncing, fileUrl)
        } catch (e: Exception) {
            rethrowCancellation(e)
            e.printStackTrace()
            null
        }
    }

    override suspend fun getActiveVote(meetingId: Int): com.example.paperlessmeeting.domain.model.Vote? {
        return try {
            api.getActiveVote(meetingId)?.toDomain()
        } catch (e: Exception) {
            rethrowCancellation(e)
            e.printStackTrace()
            null
        }
    }

    override suspend fun getVoteList(meetingId: Int): List<com.example.paperlessmeeting.domain.model.Vote> {
        return try {
            api.getVoteList(meetingId).map { it.toDomain() }
        } catch (e: Exception) {
            rethrowCancellation(e)
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun getVote(voteId: Int, userId: Int?): com.example.paperlessmeeting.domain.model.Vote? {
        return try {
            api.getVote(voteId, userId).toDomain()
        } catch (e: Exception) {
            rethrowCancellation(e)
            e.printStackTrace()
            null
        }
    }

    override suspend fun submitVote(voteId: Int, userId: Int, optionIds: List<Int>) {
        try {
            val request = com.example.paperlessmeeting.domain.model.VoteSubmitRequest(userId, optionIds)
            api.submitVote(voteId, request)
        } catch (e: Exception) {
            rethrowCancellation(e)
            e.printStackTrace()
            throw e
        }
    }

    override suspend fun getVoteResult(voteId: Int): com.example.paperlessmeeting.domain.model.VoteResult? {
        return try {
            api.getVoteResult(voteId)
        } catch (e: Exception) {
            rethrowCancellation(e)
            e.printStackTrace()
            null
        }
    }

    override suspend fun getLotteryHistory(meetingId: Int): com.example.paperlessmeeting.domain.model.LotteryHistoryResponse? {
        return try {
            api.getLotteryHistory(meetingId)
        } catch (e: Exception) {
            rethrowCancellation(e)
            e.printStackTrace()
            null
        }
    }

    override suspend fun getVoteHistory(userId: Int, skip: Int, limit: Int): List<com.example.paperlessmeeting.domain.model.Vote> {
        return api.getVoteHistory(userId, skip, limit).map { it.toDomain() }
    }

    override suspend fun getUserLotteryHistory(userId: Int): List<com.example.paperlessmeeting.domain.model.LotteryHistoryResponse> {
        return try {
            api.getUserLotteryHistory(userId)
        } catch (e: Exception) {
            rethrowCancellation(e)
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun checkIn(userId: Int, meetingId: Int): CheckInResponse {
        return api.checkIn(com.example.paperlessmeeting.domain.model.CheckInRequest(userId = userId, meetingId = meetingId))
    }

    override suspend fun cancelCheckIn(checkinId: Int, userId: Int) {
        api.cancelCheckIn(checkinId, userId)
    }
}
