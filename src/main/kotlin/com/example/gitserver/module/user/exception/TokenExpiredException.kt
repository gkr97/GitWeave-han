package com.example.gitserver.module.user.exception

import com.example.gitserver.common.exception.BusinessException
import org.springframework.http.HttpStatus

class TokenExpiredException : BusinessException(
    code = "TOKEN_EXPIRED",
    message = "JWT 토큰이 만료되었습니다.",
    status = HttpStatus.UNAUTHORIZED
)
