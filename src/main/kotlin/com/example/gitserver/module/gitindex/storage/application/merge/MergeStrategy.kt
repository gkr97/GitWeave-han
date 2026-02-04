package com.example.gitserver.module.gitindex.storage.application.merge

import com.example.gitserver.module.gitindex.shared.domain.vo.MergeType

/**
 * 병합 전략 인터페이스
 */
interface MergeStrategy {
    val type : MergeType

    fun execute(ctx : MergeContext)
}

