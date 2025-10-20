package com.example.gitserver.module.gitindex.application.merge

import com.example.gitserver.module.gitindex.domain.vo.MergeType
import org.springframework.stereotype.Component

/**
 * 병합 전략 레지스트리
 */
@Component
class MergeStrategyRegistry(strategies: List<MergeStrategy>) {
    private val map = strategies.associateBy { it.type }
    fun get(type: MergeType): MergeStrategy =
        map[type] ?: error("No MergeStrategy registered for $type")
}