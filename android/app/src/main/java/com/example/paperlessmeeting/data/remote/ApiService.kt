package com.example.paperlessmeeting.data.remote

import com.example.paperlessmeeting.domain.model.Meeting
import retrofit2.http.GET

interface ApiService {
    @GET("meetings/")
    suspend fun getMeetings(
        @retrofit2.http.Query("skip") skip: Int = 0,
        @retrofit2.http.Query("limit") limit: Int = 20,
        @retrofit2.http.Query("sort") sort: String? = "desc",
        @retrofit2.http.Query("start_date") startDate: String? = null,
        @retrofit2.http.Query("end_date") endDate: String? = null
    ): List<Meeting>

    @GET("meetings/{id}")
    suspend fun getMeeting(@retrofit2.http.Path("id") id: Int): Meeting

    @retrofit2.http.Streaming
    @GET
    suspend fun downloadFile(@retrofit2.http.Url url: String): okhttp3.ResponseBody

    @retrofit2.http.POST("auth/login")
    suspend fun login(@retrofit2.http.Body request: com.example.paperlessmeeting.domain.model.LoginRequest): com.example.paperlessmeeting.domain.model.LoginResponse

    @retrofit2.http.POST("users/change_password")
    suspend fun changePassword(@retrofit2.http.Body request: com.example.paperlessmeeting.domain.model.ChangePasswordRequest): Map<String, String>

    @retrofit2.http.POST("devices/heartbeat")
    suspend fun deviceHeartbeat(@retrofit2.http.Body heartbeat: com.example.paperlessmeeting.domain.model.DeviceHeartbeat): com.example.paperlessmeeting.domain.model.DeviceResponse

    @retrofit2.http.GET("updates/latest")
    suspend fun checkAppUpdate(): com.example.paperlessmeeting.domain.model.AppUpdateCheck?

    @retrofit2.http.GET("devices/{deviceId}/commands")
    suspend fun getDeviceCommands(@retrofit2.http.Path("deviceId") deviceId: String): List<com.example.paperlessmeeting.domain.model.DeviceCommand>

    @retrofit2.http.PUT("devices/commands/{commandId}/ack")
    suspend fun ackCommand(@retrofit2.http.Path("commandId") commandId: Int): Map<String, String>

    @retrofit2.http.Streaming
    @retrofit2.http.GET
    suspend fun downloadApk(@retrofit2.http.Url url: String): okhttp3.ResponseBody
}
