package com.example.paperlessmeeting.domain.model

data class LoginRequest(
    val query: String // Name or Phone
)

data class LoginResponse(
    val user_id: Int,
    val name: String,
    val department: String?,
    val token: String
)
