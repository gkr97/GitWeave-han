package com.example.gitserver.module.user.exception

import com.example.gitserver.common.exception.BusinessException
import org.springframework.http.HttpStatus

class RegisterUserException(
    code: String,
    message: String,
    status: HttpStatus = HttpStatus.BAD_REQUEST
) : BusinessException(code, message, status)