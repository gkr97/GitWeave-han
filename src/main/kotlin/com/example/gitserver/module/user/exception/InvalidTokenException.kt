package com.example.gitserver.module.user.exception

import com.example.gitserver.common.exception.BusinessException
import org.springframework.http.HttpStatus

class InvalidTokenException(reason: String) : BusinessException(
    code = "INVALID_TOKEN",
    message = reason,
    status = HttpStatus.UNAUTHORIZED
)