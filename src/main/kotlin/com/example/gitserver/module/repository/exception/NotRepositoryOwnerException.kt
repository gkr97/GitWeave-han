package com.example.gitserver.module.repository.exception

import com.example.gitserver.common.exception.BusinessException
import org.springframework.http.HttpStatus

class NotRepositoryOwnerException : BusinessException(
    code = "NOT_REPOSITORY_OWNER",
    message = "레포 소유자만 해당 작업을 수행할 수 있습니다.",
    status = HttpStatus.FORBIDDEN
)