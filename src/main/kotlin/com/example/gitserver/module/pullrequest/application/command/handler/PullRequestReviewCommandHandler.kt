package com.example.gitserver.module.pullrequest.application.command.handler

import com.example.gitserver.module.pullrequest.application.RequestChangesCommand
import com.example.gitserver.module.pullrequest.application.command.*
import com.example.gitserver.module.pullrequest.domain.CodeBook
import com.example.gitserver.module.pullrequest.domain.PrReviewStatus
import com.example.gitserver.module.pullrequest.domain.PrStatus
import com.example.gitserver.module.pullrequest.domain.PullRequestReviewer
import com.example.gitserver.module.pullrequest.exception.InvalidStateTransition
import com.example.gitserver.module.pullrequest.exception.NotReviewer
import com.example.gitserver.module.pullrequest.exception.PermissionDenied
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestRepository
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestReviewerRepository
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import com.example.gitserver.module.repository.exception.RepositoryNotFoundException
import com.example.gitserver.module.user.exception.UserNotFoundException
import mu.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
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
    private val codes: CodeBook,
    private val events: ApplicationEventPublisher
) {
    private val log = KotlinLogging.logger {}

    /** 리뷰어 지정 */
    @Transactional
    fun handle(cmd: AssignReviewerCommand) {
        val (repo, pr, requester) = ctx(cmd.repositoryId, cmd.pullRequestId, cmd.requesterId)
        ensureMaintainer(repo.id, requester.id)

        assertOpen(pr.statusCodeId)

        if (reviewerRepository.existsByPullRequestIdAndReviewerId(pr.id, cmd.reviewerId)) return
        val reviewer = userRepository.findByIdAndIsDeletedFalse(cmd.reviewerId)
            ?: throw UserNotFoundException(cmd.reviewerId)

        reviewerRepository.save(
            PullRequestReviewer(
                pullRequest = pr,
                reviewer = reviewer,
                statusCodeId = codes.prReviewStatusId(PrReviewStatus.PENDING),
                reviewedAt = null
            )
        )

        log.info { "[PR][Reviewer][Assign] pr=${pr.id} reviewer=${reviewer.id} by=${requester.id}" }
    }

    /** 리뷰어 해제 */
    @Transactional
    fun handle(cmd: RemoveReviewerCommand) {
        val (repo, pr, requester) = ctx(cmd.repositoryId, cmd.pullRequestId, cmd.requesterId)
        ensureMaintainer(repo.id, requester.id)
        assertOpen(pr.statusCodeId)

        reviewerRepository.findByPullRequestIdAndReviewerId(pr.id, cmd.reviewerId)?.let {
            reviewerRepository.delete(it)
            log.info { "[PR][Reviewer][Remove] pr=${pr.id} reviewer=${cmd.reviewerId} by=${requester.id}" }
        }
    }

    /** 리뷰 승인 */
    @Transactional
    fun handle(cmd: ApproveReviewCommand) {
        val (_, pr, requester) = ctx(cmd.repositoryId, cmd.pullRequestId, cmd.requesterId)
        assertOpen(pr.statusCodeId)

        val row = reviewerRepository.findByPullRequestIdAndReviewerId(pr.id, requester.id)
            ?: throw NotReviewer()
        row.statusCodeId = codes.prReviewStatusId(PrReviewStatus.APPROVED)
        row.reviewedAt = LocalDateTime.now()
        log.info { "[PR][Review][Approve] pr=${pr.id} by=${requester.id}" }
    }

    /** 변경 요청 */
    @Transactional
    fun handle(cmd: RequestChangesCommand) {
        val (_, pr, requester) = ctx(cmd.repositoryId, cmd.pullRequestId, cmd.requesterId)
        assertOpen(pr.statusCodeId)

        val row = reviewerRepository.findByPullRequestIdAndReviewerId(pr.id, requester.id)
            ?: throw NotReviewer()
        row.statusCodeId = codes.prReviewStatusId(PrReviewStatus.CHANGES_REQUESTED)
        row.reviewedAt = LocalDateTime.now()
        log.info { "[PR][Review][ChangesRequested] pr=${pr.id} by=${requester.id} reason=${cmd.reason}" }
    }

    /** 리뷰 기각 */
    @Transactional
    fun handle(cmd: DismissReviewCommand) {
        val (repo, pr, requester) = ctx(cmd.repositoryId, cmd.pullRequestId, cmd.requesterId)
        ensureMaintainer(repo.id, requester.id)
        assertOpen(pr.statusCodeId)

        val targetId = cmd.targetReviewerId ?: requester.id
        val row = reviewerRepository.findByPullRequestIdAndReviewerId(pr.id, targetId)
            ?: throw NotReviewer()
        row.statusCodeId = codes.prReviewStatusId(PrReviewStatus.DISMISSED)
        row.reviewedAt = LocalDateTime.now()
        // events.publishEvent(ReviewDismissed(pr.id, targetId, requester.id))
        log.info { "[PR][Review][Dismiss] pr=${pr.id} target=${targetId} by=${requester.id}" }
    }

    /** 머지 허용 여부(리뷰어 없으면 허용) */
    fun isMergeAllowed(prId: Long): Boolean {
        val rows = reviewerRepository.findAllByPullRequestId(prId)
        if (rows.isEmpty()) return true
        val approved = codes.prReviewStatusId(PrReviewStatus.APPROVED)
        val changes = codes.prReviewStatusId(PrReviewStatus.CHANGES_REQUESTED)
        return rows.none { it.statusCodeId == changes } && rows.all { it.statusCodeId == approved }
    }

    private fun ctx(repoId: Long, prId: Long, userId: Long) = Triple(
        repositoryRepository.findByIdAndIsDeletedFalse(repoId)
            ?: throw RepositoryNotFoundException(repoId),
        prRepository.findById(prId).orElseThrow { IllegalArgumentException("PR 없음: $prId") },
        userRepository.findByIdAndIsDeletedFalse(userId)
            ?: throw UserNotFoundException(userId)
    )

    private fun ensureMaintainer(repoId: Long, userId: Long) {
        val repo = repositoryRepository.findByIdAndIsDeletedFalse(repoId)
            ?: throw RepositoryNotFoundException(repoId)
        val owner = repo.owner.id == userId
        val collab = collaboratorRepository.existsByRepositoryIdAndUserId(repoId, userId)
        if (!(owner || collab)) throw PermissionDenied()
    }

    private fun assertOpen(statusId: Long) {
        val openId = codes.prStatusId(PrStatus.OPEN)
        if (statusId != openId) throw InvalidStateTransition("open 상태에서만 허용")
    }
}
