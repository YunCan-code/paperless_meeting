package com.example.paperlessmeeting.domain.model

data class LoginRequest(
    val query: String // Name or Phone
)

data class LoginResponse(
    val user_id: Int,
    val name: String,
    val department: String?,
    val district: String?,
    val phone: String?,
    val email: String?,
    val role: String?,
    val token: String
)

data class ChangePasswordRequest(
    val user_id: Int,
    val old_password: String,
    val new_password: String
)

data class UserProfileUpdate(
    val department: String? = null,
    val district: String? = null,
    val phone: String? = null,
    val email: String? = null
)
