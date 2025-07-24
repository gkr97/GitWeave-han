package com.example.gitserver.module.user.domain.vo

import java.time.Instant

data class RefreshToken(
    val userId: Long,
    val value: String,
    val expiredAt: Instant
)