package com.example.gitserver.module.gitindex.exception

class GitCommitParseException(cause: Throwable) : GitIndexingException(
    code = "GIT_COMMIT_PARSE_ERROR",
    message = "커밋 파싱 중 오류 발생",
    cause = cause
)