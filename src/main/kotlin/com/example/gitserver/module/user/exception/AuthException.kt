package com.example.gitserver.module.user.exception

import com.example.gitserver.common.exception.BusinessException
import org.springframework.http.HttpStatus

class AuthException(
    code: String,
    message: String,
    status: HttpStatus = HttpStatus.UNAUTHORIZED,
    cause: Throwable? = null
) : BusinessException(code, message, status, cause)