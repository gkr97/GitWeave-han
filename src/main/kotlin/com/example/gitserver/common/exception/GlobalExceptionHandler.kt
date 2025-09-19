package com.example.gitserver.common.exception

import com.example.gitserver.common.response.ErrorResponse
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.time.Instant

private val logger = KotlinLogging.logger {}

@RestControllerAdvice
class GlobalExceptionHandler {

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

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val errorMsg = ex.bindingResult.fieldErrors.joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        logger.warn { "ValidationException: $errorMsg" }
        val response = ErrorResponse(
            code = "VALIDATION_ERROR",
            message = errorMsg,
            timestamp = Instant.now()
        )
        return ResponseEntity.badRequest().body(response)
    }

    @ExceptionHandler(
        MissingServletRequestParameterException::class,
        MethodArgumentTypeMismatchException::class
    )
    fun handleBadRequest(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.warn { "BadRequestException: ${ex.message}" }
        val response = ErrorResponse(
            code = "BAD_REQUEST",
            message = ex.message ?: "Bad request",
            timestamp = Instant.now()
        )
        return ResponseEntity.badRequest().body(response)
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(ex: HttpRequestMethodNotSupportedException): ResponseEntity<ErrorResponse> {
        logger.warn { "MethodNotSupportedException: ${ex.message}" }
        val response = ErrorResponse(
            code = "METHOD_NOT_ALLOWED",
            message = ex.message ?: "HTTP Method not allowed",
            timestamp = Instant.now()
        )
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response)
    }

    @ExceptionHandler(Exception::class)
    fun handleException(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error(ex) { "Unhandled Exception" }
        val response = ErrorResponse(
            code = "INTERNAL_ERROR",
            message = ex.message ?: "Internal server error",
            timestamp = Instant.now()
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response)
    }

    @ExceptionHandler(SecurityException::class)
    fun handleSecurityException(ex: SecurityException): ResponseEntity<ErrorResponse> {
        val response = ErrorResponse(
            code = "FORBIDDEN",
            message = ex.message ?: "Forbidden",
            timestamp = Instant.now()
        )
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response)
    }
}
