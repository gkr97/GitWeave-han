package com.example.gitserver.module.gitindex.shared.exception

class AuthorNotFoundException(email: String) : GitIndexingException(
    code = "AUTHOR_NOT_FOUND",
    message = "작성자 이메일로 사용자를 찾을 수 없습니다: $email"
)