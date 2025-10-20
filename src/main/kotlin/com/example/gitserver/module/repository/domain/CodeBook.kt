package com.example.gitserver.module.repository.domain

import com.example.gitserver.module.common.application.service.CommonCodeCacheService
import org.springframework.stereotype.Component

@Component
class CodeBook(private val codes: CommonCodeCacheService) {
    fun visibilityId(code: String): Long =
        codes.getCodeDetailsOrLoad("VISIBILITY")
            .firstOrNull { it.code.equals(code, true) }
            ?.id ?: error("VISIBILITY.$code not found")
}