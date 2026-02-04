package com.example.gitserver.module.gitindex.shared.exception

import com.example.gitserver.common.exception.BusinessException
import org.springframework.http.HttpStatus

open class GitIndexingException(
    code: String,
    message: String,
    status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR,
    cause: Throwable? = null
) : BusinessException(code, message, status, cause)