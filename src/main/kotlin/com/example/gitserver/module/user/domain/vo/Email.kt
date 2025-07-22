package com.example.gitserver.module.user.domain.vo

@JvmInline
value class Email(val value: String) {
    init {
        require(value.contains("@")) { "이메일 형식이 아닙니다." }
    }
} 