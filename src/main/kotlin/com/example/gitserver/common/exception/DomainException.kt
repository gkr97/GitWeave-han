package com.example.gitserver.common.exception


/**
 * 도메인 예외의 기본 클래스입니다.
 *
 * @property message 예외 메시지
 * @property cause 원인 예외 (기본값: null)
 */
open class DomainException(
    override val message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)


// 테스트용 마커 인터페이스
interface NotFoundMarker
interface ForbiddenMarker
interface ConflictMarker
interface ValidationMarker