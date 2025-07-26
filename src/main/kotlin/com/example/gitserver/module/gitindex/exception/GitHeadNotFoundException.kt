package com.example.gitserver.module.gitindex.exception

class GitHeadNotFoundException : GitIndexingException(
    code = "GIT_HEAD_NOT_FOUND",
    message = "HEAD 참조를 찾을 수 없습니다."
)