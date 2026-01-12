package com.example.paperlessmeeting.domain.model

data class LoginRequest(
    val query: String // Name or Phone
)

data class LoginResponse(
    val user_id: Int,
    val name: String,
    val department: String?,
    val role: String?,
    val token: String
)

data class ChangePasswordRequest(
    val user_id: Int,
    val old_password: String,
    val new_password: String
)
