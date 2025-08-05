package com.example.gitserver.module.user.interfaces.dto

data class RefreshRequest(
    val accessToken: String,
    val refreshToken: String
)