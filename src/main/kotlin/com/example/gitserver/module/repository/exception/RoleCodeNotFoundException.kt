package com.example.gitserver.module.repository.exception

import com.example.gitserver.common.exception.BusinessException
import org.springframework.http.HttpStatus

class RoleCodeNotFoundException : BusinessException(
    code = "ROLE_CODE_NOT_FOUND",
    message = "유효한 역할 코드가 없습니다.",
    status = HttpStatus.INTERNAL_SERVER_ERROR
)