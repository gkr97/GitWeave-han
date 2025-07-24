package com.example.gitserver.module.user.domain.event

import java.time.LocalDateTime

data class UserLoginEvent(
    val userId: Long,
    val ipAddress: String,
    val userAgent: String?,
    val loginAt: LocalDateTime,
    val success: Boolean
)