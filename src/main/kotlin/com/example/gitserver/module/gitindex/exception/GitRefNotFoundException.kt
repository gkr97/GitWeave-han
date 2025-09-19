package com.example.gitserver.module.gitindex.exception

import org.springframework.http.HttpStatus

class GitRefNotFoundException(
    ref: String,
    cause: Throwable? = null
) : GitMergeException(
    code = "GIT_REF_NOT_FOUND",
    message = "Git 참조를 찾을 수 없습니다: $ref",
    status = HttpStatus.NOT_FOUND,
    cause = cause
)