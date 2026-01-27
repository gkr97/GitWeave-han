package com.example.gitserver.module.gitindex.domain.vo

import com.example.gitserver.common.util.PathSecurityUtils

/**
 * 파일 경로를 나타내는 Value Object
 * Path Traversal 공격을 방어하기 위해 검증을 수행합니다.
 */
@JvmInline
value class FilePath(val value: String) {
    init {
        require(value.isNotBlank()) { "경로는 비어 있을 수 없습니다." }
        // Path Traversal 방어 검증
        try {
            PathSecurityUtils.sanitizePath(value)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("잘못된 파일 경로: ${e.message}", e)
        }
    }
} 