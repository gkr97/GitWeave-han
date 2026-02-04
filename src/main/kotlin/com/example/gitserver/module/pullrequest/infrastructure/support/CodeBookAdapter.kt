package com.example.gitserver.module.pullrequest.infrastructure.support

import com.example.gitserver.module.common.application.service.CommonCodeCacheService
import com.example.gitserver.module.gitindex.shared.domain.vo.MergeType
import com.example.gitserver.module.pullrequest.domain.CodeBook
import com.example.gitserver.module.pullrequest.domain.PrMergeType
import com.example.gitserver.module.pullrequest.domain.PrReviewStatus
import com.example.gitserver.module.pullrequest.domain.PrStatus
import org.springframework.stereotype.Component

@Component
class CodeBookAdapter(
    private val common: CommonCodeCacheService
) : CodeBook {

    override fun prStatusId(status: PrStatus): Long =
        common.getCodeDetailsOrLoad("PR_STATUS")
            .firstOrNull { it.code.equals(status.name.lowercase(), true) }
            ?.id ?: error("PR_STATUS.${status.name} 미정의")

    override fun prReviewStatusId(status: PrReviewStatus): Long =
        common.getCodeDetailsOrLoad("PR_REVIEW_STATUS")
            .firstOrNull { it.code.equals(status.name.lowercase(), true) }
            ?.id ?: error("PR_REVIEW_STATUS.${status.name} 미정의")

    override fun prMergeTypeId(type: PrMergeType): Long =
        common.getCodeDetailsOrLoad("PR_MERGE_TYPE")
            .firstOrNull { it.code.equals(type.code, true) }
            ?.id ?: error("PR_MERGE_TYPE.${type.code} 미정의")

    override fun toGitMergeType(type: PrMergeType): MergeType = when (type) {
        PrMergeType.MERGE_COMMIT -> MergeType.MERGE_COMMIT
        PrMergeType.SQUASH -> MergeType.SQUASH
        PrMergeType.REBASE -> MergeType.REBASE
    }
}