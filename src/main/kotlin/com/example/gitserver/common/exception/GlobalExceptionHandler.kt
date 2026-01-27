package com.example.gitserver.common.exception

import com.example.gitserver.common.response.ErrorResponse
import mu.KotlinLogging
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.core.env.Environment
import org.springframework.web.server.ResponseStatusException
import java.time.Instant


private val logger = KotlinLogging.logger {}

@RestControllerAdvice
class GlobalExceptionHandler(
    private val environment: Environment
) {
    
    private val isDevelopment: Boolean
        get() = environment.activeProfiles.contains("dev")

    /** [ADD] 도메인 예외 매핑 */
    @ExceptionHandler(DomainException::class)
    fun handleDomainException(ex: DomainException): ResponseEntity<ErrorResponse> {
        val (status, code) = when (ex) {
            is NotFoundMarker   -> HttpStatus.NOT_FOUND    to "NOT_FOUND"
            is ForbiddenMarker  -> HttpStatus.FORBIDDEN    to "FORBIDDEN"
            is ConflictMarker   -> HttpStatus.CONFLICT     to "CONFLICT"
            is ValidationMarker -> HttpStatus.BAD_REQUEST  to "VALIDATION_ERROR"
            else                -> HttpStatus.BAD_REQUEST  to "DOMAIN_ERROR"
        }
        logger.warn { "DomainException[$code]: ${ex.message}" }
        return ResponseEntity.status(status).body(
            ErrorResponse(code = code, message = ex.message ?: "Domain error", timestamp = Instant.now())
        )
    }

    /** 비즈니스 예외를 HTTP 상태 코드에 매핑 */
    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(ex: BusinessException): ResponseEntity<ErrorResponse> {
        logger.warn { "BusinessException: code=${ex.code}, message=${ex.message}" }
        val response = ErrorResponse(
            code = ex.code,
            message = ex.message ?: "Business Exception",
            timestamp = Instant.now()
        )
        return ResponseEntity.status(ex.status).body(response)
    }

    /** @Valid 검증 실패 시 400 */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val errorMsg = ex.bindingResult.fieldErrors.joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        logger.warn { "ValidationException: $errorMsg" }
        // 개발 환경에서만 상세 메시지 제공
        val isDevelopment = System.getProperty("spring.profiles.active")?.contains("dev") == true
        val responseMessage = if (isDevelopment) {
            errorMsg
        } else {
            "Validation failed. Please check your input."
        }
        return ResponseEntity.badRequest().body(
            ErrorResponse(code = "VALIDATION_ERROR", message = responseMessage, timestamp = Instant.now())
        )
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(e: ResponseStatusException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(e.statusCode)
            .body(
                ErrorResponse(
                    code = e.statusCode.toString(),
                    message = e.reason ?: "Error",
                    timestamp = Instant.now()
                )
            )
    }

    /** 잘못된 요청 파라미터 400 */
    @ExceptionHandler(
        MissingServletRequestParameterException::class,
        MethodArgumentTypeMismatchException::class
    )
    fun handleBadRequest(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.warn { "BadRequestException: ${ex.javaClass.simpleName}" }
        // 파라미터 이름 등 민감 정보는 로그에만 기록
        val errorMessage = if (isDevelopment) {
            ex.message ?: "Bad request"
        } else {
            "Invalid request parameters"
        }
        return ResponseEntity.badRequest().body(
            ErrorResponse(code = "BAD_REQUEST", message = errorMessage, timestamp = Instant.now())
        )
    }

    /** 지원하지 않는 HTTP 메서드 405 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(ex: HttpRequestMethodNotSupportedException): ResponseEntity<ErrorResponse> {
        logger.warn { "MethodNotSupportedException: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(
            ErrorResponse(code = "METHOD_NOT_ALLOWED", message = ex.message ?: "HTTP Method not allowed", timestamp = Instant.now())
        )
    }

    /** 인증/인가 실패 401, 403 */
    @ExceptionHandler(SecurityException::class)
    fun handleSecurityException(ex: SecurityException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ErrorResponse(code = "FORBIDDEN", message = ex.message ?: "Forbidden", timestamp = Instant.now())
        )
    }

    /** 데이터 무결성/동시성 충돌 409 */
    @ExceptionHandler(DataIntegrityViolationException::class, ObjectOptimisticLockingFailureException::class)
    fun handleConflict(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.warn(ex) { "ConflictException: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(code = "CONFLICT", message = "동시성 충돌 또는 무결성 제약 위반", timestamp = Instant.now())
        )
    }

    /** 그 외 500 */
    @ExceptionHandler(Exception::class)
    fun handleException(ex: Exception): ResponseEntity<ErrorResponse> {
        // 민감한 정보 노출 방지: 스택 트레이스는 로그에만 기록
        logger.error(ex) { "Unhandled Exception: ${ex.javaClass.simpleName}" }
        
        // 프로덕션 환경에서는 상세 에러 메시지 숨김
        val errorMessage = if (isDevelopment) {
            ex.message ?: "Internal server error"
        } else {
            "Internal server error. Please contact support if the problem persists."
        }
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(code = "INTERNAL_ERROR", message = errorMessage, timestamp = Instant.now())
        )
    }
}
