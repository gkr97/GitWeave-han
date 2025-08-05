package com.example.gitserver.module.user.interfaces.dto

data class EmailVerificationMessage(
    val userId: Long,
    val email: String,
    val subject: String,
    val body: String,
    val token: String
)
