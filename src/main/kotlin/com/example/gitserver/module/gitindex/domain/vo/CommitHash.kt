package com.example.gitserver.module.gitindex.domain.vo

@JvmInline
value class CommitHash(val value: String) {
    init {
        require(value.length == 40) { "SHA-1 해시값이어야 합니다." }
        require(value.matches(Regex("^[0-9a-fA-F]{40}$"))) { "SHA-1 해시는 16진수 40자리 문자열이어야 합니다." }
    }
} 