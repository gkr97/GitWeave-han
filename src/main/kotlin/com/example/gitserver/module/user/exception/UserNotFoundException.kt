package com.example.gitserver.module.user.exception

import com.example.gitserver.common.exception.BusinessException
import org.springframework.http.HttpStatus

class UserNotFoundException(userId: Long) : BusinessException(
    code = "USER_NOT_FOUND",
    message = "사용자를 찾을 수 없습니다. (id: $userId)",
    status = HttpStatus.NOT_FOUND
)