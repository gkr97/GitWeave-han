package com.example.gitserver.module.pullrequest.application.command.handler

import com.example.gitserver.module.common.service.CommonCodeCacheService
import com.example.gitserver.module.pullrequest.application.RequestChangesCommand
import com.example.gitserver.module.pullrequest.application.command.*
import com.example.gitserver.module.pullrequest.domain.PullRequestReviewer
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestRepository
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestReviewerRepository
import com.example.gitserver.module.repository.exception.RepositoryAccessDeniedException
import com.example.gitserver.module.repository.exception.RepositoryNotFoundException
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class PullRequestReviewCommandHandler(
    private val repositoryRepository: RepositoryRepository,
    private val collaboratorRepository: CollaboratorRepository,
    private val userRepository: UserRepository,
    private val prRepository: PullRequestRepository,
    private val reviewerRepository: PullRequestReviewerRepository,
    private val commonCode: CommonCodeCacheService
) {
    private val log = KotlinLogging.logger {}


    /** 리뷰어 지정/해제, 리뷰 승인/변경요청/기각 */
    @Transactional
    fun handle(cmd: AssignReviewerCommand) {
        val (repo, pr, requester) = ensureContext(cmd.repositoryId, cmd.pullRequestId, cmd.requesterId)
        if (!canManageReviewers(repo.id, requester.id)) throw RepositoryAccessDeniedException(repo.id, requester.id)
        assertPrOpen(pr.statusCodeId)

        if (reviewerRepository.existsByPullRequestIdAndReviewerId(pr.id, cmd.reviewerId)) return
        val reviewer = userRepository.findByIdAndIsDeletedFalse(cmd.reviewerId)
            ?: throw IllegalArgumentException("리뷰어 없음: ${cmd.reviewerId}")
        reviewerRepository.save(
            PullRequestReviewer(
                pullRequest = pr,
                reviewer = reviewer,
                statusCodeId = statusId("pending"),
                reviewedAt = null
            )
        )
        log.info { "[PR][Reviewer][Assign] pr=${pr.id} -> user=${reviewer.id} by=${requester.id}" }
    }

    /**
     * 리뷰어 지정 해제
     */
    @Transactional
    fun handle(cmd: RemoveReviewerCommand) {
        val (repo, pr, requester) = ensureContext(cmd.repositoryId, cmd.pullRequestId, cmd.requesterId)
        if (!canManageReviewers(repo.id, requester.id)) throw RepositoryAccessDeniedException(repo.id, requester.id)
        assertPrOpen(pr.statusCodeId)

        reviewerRepository.findByPullRequestIdAndReviewerId(pr.id, cmd.reviewerId)?.let {
            reviewerRepository.delete(it)
            log.info { "[PR][Reviewer][Remove] pr=${pr.id} user=${cmd.reviewerId} by=${requester.id}" }
        }
    }

    /** 리뷰 승인 */
    @Transactional
    fun handle(cmd: ApproveReviewCommand) {
        val (_, pr, requester) = ensureContext(cmd.repositoryId, cmd.pullRequestId, cmd.requesterId)
        assertPrOpen(pr.statusCodeId)

        val row = reviewerRepository.findByPullRequestIdAndReviewerId(pr.id, requester.id)
            ?: error("리뷰어가 아닙니다.")
        row.statusCodeId = statusId("approved")
        row.reviewedAt = LocalDateTime.now()
        log.info { "[PR][Review][Approve] pr=${pr.id} by=${requester.id}" }
    }

    /** 변경 요청 */
    @Transactional
    fun handle(cmd: RequestChangesCommand) {
        val (_, pr, requester) = ensureContext(cmd.repositoryId, cmd.pullRequestId, cmd.requesterId)
        assertPrOpen(pr.statusCodeId)

        val row = reviewerRepository.findByPullRequestIdAndReviewerId(pr.id, requester.id)
            ?: error("리뷰어가 아닙니다.")
        row.statusCodeId = statusId("changes_requested")
        row.reviewedAt = LocalDateTime.now()
        log.info { "[PR][Review][ChangesRequested] pr=${pr.id} by=${requester.id} reason=${cmd.reason}" }
    }

    /** 리뷰 기각 */
    @Transactional
    fun handle(cmd: DismissReviewCommand) {
        val (repo, pr, requester) = ensureContext(cmd.repositoryId, cmd.pullRequestId, cmd.requesterId)
        if (!canManageReviewers(repo.id, requester.id)) throw RepositoryAccessDeniedException(repo.id, requester.id)
        assertPrOpen(pr.statusCodeId)

        val targetId = cmd.targetReviewerId ?: requester.id
        val row = reviewerRepository.findByPullRequestIdAndReviewerId(pr.id, targetId)
            ?: error("해당 리뷰어 없음.")
        row.statusCodeId = statusId("dismissed")
        row.reviewedAt = LocalDateTime.now()
        log.info { "[PR][Review][Dismiss] pr=${pr.id} target=${targetId} by=${requester.id}" }
    }


    private fun statusId(code: String): Long =
        commonCode.getCodeDetailsOrLoad("PR_REVIEW_STATUS")
            .firstOrNull { it.code.equals(code, true) }?.id
            ?: error("PR_REVIEW_STATUS.$code 미정의")

    private fun prStatusId(code: String): Long =
        commonCode.getCodeDetailsOrLoad("PR_STATUS")
            .firstOrNull { it.code.equals(code, true) }?.id
            ?: error("PR_STATUS.$code 미정의")

    private fun ensureContext(repoId: Long, prId: Long, userId: Long) = Triple(
        repositoryRepository.findByIdAndIsDeletedFalse(repoId)
            ?: throw RepositoryNotFoundException(repoId),
        prRepository.findById(prId).orElseThrow { IllegalArgumentException("PR 없음: $prId") },
        userRepository.findByIdAndIsDeletedFalse(userId)
            ?: throw IllegalArgumentException("유저 없음: $userId")
    )

    private fun canManageReviewers(repoId: Long, userId: Long): Boolean =
        repositoryRepository.findByIdAndIsDeletedFalse(repoId)?.let { repo ->
            (repo.owner.id == userId) || collaboratorRepository.existsByRepositoryIdAndUserId(repo.id, userId)
        } ?: false

    private fun assertPrOpen(prStatusId: Long) {
        val openId = prStatusId("open")
        if (prStatusId != openId) error("open 상태에서만 리뷰 변경 가능합니다.")
    }

    /** 머지 가능 여부(정책: 리뷰어 없으면 허용) */
    fun isMergeAllowed(prId: Long): Boolean {
        val rows = reviewerRepository.findAllByPullRequestId(prId)
        if (rows.isEmpty()) return true
        val approved = statusId("approved")
        val changes = statusId("changes_requested")
        return rows.none { it.statusCodeId == changes } && rows.all { it.statusCodeId == approved }
    }
}
