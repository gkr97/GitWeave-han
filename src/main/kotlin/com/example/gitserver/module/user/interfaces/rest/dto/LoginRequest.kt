package com.example.gitserver.module.user.interfaces.rest.dto

data class LoginRequest(
    val email: String,
    val password: String
)
