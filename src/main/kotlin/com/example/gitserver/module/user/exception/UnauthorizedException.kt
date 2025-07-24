package com.example.gitserver.module.user.exception

import com.example.gitserver.common.exception.BusinessException
import org.springframework.http.HttpStatus

class UnauthorizedException(
    code: String,
    message: String,
    status: HttpStatus = HttpStatus.UNAUTHORIZED
) : BusinessException(code, message, status)