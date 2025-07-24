package com.example.gitserver.module.user.interfaces.rest.dto

data class RefreshRequest(
    val accessToken: String,
    val refreshToken: String
)