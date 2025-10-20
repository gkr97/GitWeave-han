package com.example.gitserver.common.util

import java.util.*

/**
 * 토큰 생성 유틸리티 클래스
 */
object TokenUtils {
    fun generateVerificationToken(): String = UUID.randomUUID().toString()
}