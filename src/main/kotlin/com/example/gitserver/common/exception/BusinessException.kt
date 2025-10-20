package com.example.gitserver.common.exception

import org.springframework.http.HttpStatus

/**
 * 비즈니스 로직 예외의 기본 클래스입니다.
 *
 * @property code 예외 코드
 * @property message 예외 메시지
 * @property status HTTP 상태 코드 (기본값: 400 BAD_REQUEST)
 * @property cause 원인 예외 (기본값: null)
 */
open class BusinessException(
    val code: String,
    override val message: String,
    val status: HttpStatus = HttpStatus.BAD_REQUEST,
    cause: Throwable? = null
) : RuntimeException(message, cause)