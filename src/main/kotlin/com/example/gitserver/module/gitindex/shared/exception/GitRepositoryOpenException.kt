package com.example.gitserver.module.gitindex.shared.exception

class GitRepositoryOpenException(path: String, cause: Throwable? = null) : GitIndexingException(
    code = "GIT_REPO_OPEN_FAILED",
    message = "Git 저장소 열기에 실패했습니다: $path",
    cause = cause
)