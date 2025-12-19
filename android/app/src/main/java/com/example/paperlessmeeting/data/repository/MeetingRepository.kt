package com.example.paperlessmeeting.data.repository

import com.example.paperlessmeeting.data.remote.ApiService
import com.example.paperlessmeeting.domain.model.Meeting
import javax.inject.Inject
import javax.inject.Singleton

interface MeetingRepository {
    suspend fun getMeetings(): List<Meeting>
    suspend fun getMeetingById(id: Int): Meeting?
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
}
