package com.example.gitserver.module.pullrequest.application.query

import com.example.gitserver.module.common.application.service.CommonCodeCacheService
import com.example.gitserver.module.pullrequest.application.query.model.PullRequestReviewSummaryView
import com.example.gitserver.module.pullrequest.application.query.model.PullRequestReviewerView
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestReviewerRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional


@Service
class PullRequestReviewQueryService(
    private val reviewerRepo: PullRequestReviewerRepository,
    private val code: CommonCodeCacheService
) {
    private val log = KotlinLogging.logger {}

    private fun codeById(id: Long): String {
        val all = code.getCodeDetailsOrLoad("PR_REVIEW_STATUS")
        return all.firstOrNull { it.id == id }?.code ?: "unknown"
    }

    /**
     * 특정 Pull Request에 대한 리뷰어 목록을 조회합니다.
     *
     * @param prId Pull Request ID
     * @return 리뷰어 목록
     */
    @Transactional(readOnly = true)
    fun listReviewers(prId: Long): List<PullRequestReviewerView> {
        log.info { "Checking for reviewers for PR $prId" }
        val rows = reviewerRepo.findAllByPullRequestId(prId)
        return rows.map {
            PullRequestReviewerView(
                userId = it.reviewer.id,
                nickname = it.reviewer.name ?: "unknown",
                profileImageUrl = it.reviewer.profileImageUrl,
                status = codeById(it.statusCodeId),
                reviewedAt = it.reviewedAt?.toString()
            )
        }
    }

    /**
     * 특정 Pull Request에 대한 리뷰 요약 정보를 조회합니다.
     *
     * @param prId Pull Request ID
     * @return 리뷰 요약 정보
     */
    @Transactional(readOnly = true)
    fun summary(prId: Long): PullRequestReviewSummaryView {
        log.info { "Checking for reviewers for PR $prId" }
        val counts = reviewerRepo.countByStatusGrouped(prId)
        val map = counts.associate { (it[0] as Long) to (it[1] as Long).toInt() }
        fun n(codeStr: String) =
            map.entries.firstOrNull { codeById(it.key).equals(codeStr, true) }?.value ?: 0
        val pending = n("pending")
        val approved = n("approved")
        val changes = n("changes_requested")
        val dismissed = n("dismissed")

        log.info { "Review summary for PR $prId: pending=$pending, approved=$approved, changes=$changes, dismissed=$dismissed" }
        return PullRequestReviewSummaryView(
            total = pending + approved + changes + dismissed,
            pending = pending, approved = approved, changesRequested = changes, dismissed = dismissed
        )
    }
}
