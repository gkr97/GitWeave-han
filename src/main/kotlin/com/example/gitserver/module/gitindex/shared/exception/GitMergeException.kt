package com.example.gitserver.module.gitindex.shared.exception

import org.springframework.http.HttpStatus

open class GitMergeException(
    code: String,
    message: String,
    status: HttpStatus = HttpStatus.CONFLICT,
    cause: Throwable? = null
) : GitIndexingException(code, message, status, cause)