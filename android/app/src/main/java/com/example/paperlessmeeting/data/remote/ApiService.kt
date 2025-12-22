package com.example.paperlessmeeting.data.remote

import com.example.paperlessmeeting.domain.model.Meeting
import retrofit2.http.GET

interface ApiService {
    @GET("meetings/")
    suspend fun getMeetings(): List<Meeting>

    @GET("meetings/{id}")
    suspend fun getMeeting(@retrofit2.http.Path("id") id: Int): Meeting

    @retrofit2.http.Streaming
    @GET
    suspend fun downloadFile(@retrofit2.http.Url url: String): okhttp3.ResponseBody

    @retrofit2.http.POST("auth/login")
    suspend fun login(@retrofit2.http.Body request: com.example.paperlessmeeting.domain.model.LoginRequest): com.example.paperlessmeeting.domain.model.LoginResponse
}
