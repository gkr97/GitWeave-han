package com.example.gitserver.module.user.interfaces.rest.dto

data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserResponse? = null
)