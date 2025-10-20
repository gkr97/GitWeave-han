package com.example.gitserver.common.response

import java.time.Instant

/**
 * 에러 응답 포맷
 */
data class ErrorResponse(
    val code: String,
    val message: String,
    val timestamp: Instant = Instant.now()
) 