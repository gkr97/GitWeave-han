package com.example.gitserver.module.gitindex.exception

import org.springframework.http.HttpStatus

class GitMergeFailedException(
    sourceRef: String,
    targetRef: String,
    detail: String? = null,
    cause: Throwable? = null
) : GitMergeException(
    code = "GIT_MERGE_FAILED",
    message = buildString {
        append("병합 실패: $sourceRef -> $targetRef")
        if (!detail.isNullOrBlank()) append(" ($detail)")
    },
    status = HttpStatus.INTERNAL_SERVER_ERROR,
    cause = cause
)