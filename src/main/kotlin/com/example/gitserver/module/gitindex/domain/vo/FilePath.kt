package com.example.gitserver.module.gitindex.domain.vo

@JvmInline
value class FilePath(val value: String) {
    init {
        require(value.isNotBlank()) { "경로는 비어 있을 수 없습니다." }
    }
} 