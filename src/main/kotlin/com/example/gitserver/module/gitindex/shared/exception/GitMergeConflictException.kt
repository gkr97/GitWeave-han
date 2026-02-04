package com.example.gitserver.module.gitindex.shared.exception

import org.springframework.http.HttpStatus

class GitMergeConflictException(
    sourceRef: String,
    targetRef: String,
    detail: String? = null,
    cause: Throwable? = null
) : GitMergeException(
    code = "GIT_MERGE_CONFLICT",
    message = buildString {
        append("병합 충돌: $sourceRef -> $targetRef")
        if (!detail.isNullOrBlank()) append(" ($detail)")
    },
    status = HttpStatus.CONFLICT,
    cause = cause
)