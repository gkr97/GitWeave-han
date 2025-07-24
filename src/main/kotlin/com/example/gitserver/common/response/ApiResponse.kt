package com.example.gitserver.common.response

import java.time.Instant

/**
 * API 응답 통일 포맷
 */
data class ApiResponse<T>(
    val success: Boolean,
    val code: Int,
    val message: String,
    val data: T? = null,
    val timestamp: Instant = Instant.now(),
) {
    companion object {
        fun <T> success(code: Int, message: String, data: T? = null): ApiResponse<T> =
            ApiResponse(true, code, message, data)

        fun error(code: Int, message: String): ApiResponse<Nothing> =
            ApiResponse(false, code, message, null)
    }
}
