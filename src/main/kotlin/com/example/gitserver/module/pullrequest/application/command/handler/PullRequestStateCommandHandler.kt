package com.example.gitserver.module.pullrequest.application.command.handler

import com.example.gitserver.module.pullrequest.application.MergePullRequestCommand
import com.example.gitserver.module.pullrequest.application.command.ClosePullRequestCommand
import com.example.gitserver.module.pullrequest.application.command.ReopenPullRequestCommand
import com.example.gitserver.module.pullrequest.domain.PrMergeType
import com.example.gitserver.module.pullrequest.domain.PrStatus
import com.example.gitserver.module.pullrequest.domain.PullRequestMergeLog
import com.example.gitserver.module.pullrequest.domain.CodeBook
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestMergeLogRepository
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestRepository
import com.example.gitserver.module.repository.application.command.handler.GitRepositorySyncHandler
import com.example.gitserver.module.repository.domain.event.GitEventPublisher
import com.example.gitserver.module.repository.domain.event.SyncBranchEvent
import com.example.gitserver.module.repository.exception.RepositoryNotFoundException
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import com.example.gitserver.module.gitindex.domain.dto.MergeRequest
import com.example.gitserver.module.gitindex.domain.event.GitEvent
import com.example.gitserver.module.gitindex.domain.port.GitRepositoryPort
import com.example.gitserver.module.gitindex.infrastructure.redis.GitIndexEvictor
import com.example.gitserver.module.pullrequest.exception.*
import com.example.gitserver.module.user.exception.UserNotFoundException
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class PullRequestStateCommandHandler(
    private val repositoryRepository: RepositoryRepository,
    private val collaboratorRepository: CollaboratorRepository,
    private val userRepository: UserRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val mergeLogRepository: PullRequestMergeLogRepository,
    private val codes: CodeBook,
    private val reviewCommandHandler: PullRequestReviewCommandHandler,
    private val gitRepositoryPort: GitRepositoryPort,
    private val gitRepositorySyncHandler: GitRepositorySyncHandler,
    private val gitEventPublisher: GitEventPublisher,
    private val gitIndexEvictor: GitIndexEvictor,
) {
    private val log = KotlinLogging.logger {}

    companion object {
        private const val EMPTY_SHA1 = "0000000000000000000000000000000000000000"
    }

    /** repoId/userId/authorId 중 하나라도 권한 충족(소유자/협업자/작성자) */
    private fun ensurePerm(repoId: Long, userId: Long, alsoAuthorId: Long? = null) {
        val repo = repositoryRepository.findByIdAndIsDeletedFalse(repoId) ?: throw RepositoryNotFoundException(repoId)
        val owner = repo.owner.id == userId
        val collab = collaboratorRepository.existsByRepositoryIdAndUserId(repoId, userId)
        val authorOk = alsoAuthorId?.let { it == userId } ?: false
        if (!(owner || collab || authorOk)) throw PermissionDenied()
    }

    /** 소유자/협업자만 허용(머지 등) */
    private fun ensureMaintainerPerm(repoId: Long, userId: Long) {
        val repo = repositoryRepository.findByIdAndIsDeletedFalse(repoId) ?: throw RepositoryNotFoundException(repoId)
        val owner = repo.owner.id == userId
        val collab = collaboratorRepository.existsByRepositoryIdAndUserId(repoId, userId)
        if (!(owner || collab)) throw PermissionDenied()
    }

    /** PR이 OPEN 상태인지 확인 */
    @Transactional
    fun handle(cmd: ClosePullRequestCommand) {
        val repo = repositoryRepository.findByIdAndIsDeletedFalse(cmd.repositoryId)
            ?: throw RepositoryNotFoundException(cmd.repositoryId)
        val requester = userRepository.findByIdAndIsDeletedFalse(cmd.requesterId)
            ?: throw UserNotFoundException(cmd.requesterId)
        val pr = pullRequestRepository.findById(cmd.pullRequestId)
            .orElseThrow { IllegalArgumentException("PR 없음: ${cmd.pullRequestId}") }

        if (pr.repository.id != repo.id) throw RepositoryMismatch(repo.id, pr.id)
        ensurePerm(repo.id, requester.id, pr.author.id)

        val openId = codes.prStatusId(PrStatus.OPEN)
        val closedId = codes.prStatusId(PrStatus.CLOSED)
        if (pr.statusCodeId != openId) throw InvalidStateTransition("open 상태 에서만 close 가능합니다.")

        pr.statusCodeId = closedId
        pr.closedAt = LocalDateTime.now()
        pr.updatedAt = pr.closedAt
        pullRequestRepository.save(pr)

        log.info { "[PR][Close] pr=${pr.id} repo=${repo.id} by=${requester.id}" }
    }

    /** PR이 CLOSED 상태인지 확인 */
    @Transactional
    fun handle(cmd: ReopenPullRequestCommand) {
        val repo = repositoryRepository.findByIdAndIsDeletedFalse(cmd.repositoryId)
            ?: throw RepositoryNotFoundException(cmd.repositoryId)
        val requester = userRepository.findByIdAndIsDeletedFalse(cmd.requesterId)
            ?: throw UserNotFoundException(cmd.requesterId)
        val pr = pullRequestRepository.findById(cmd.pullRequestId)
            .orElseThrow { IllegalArgumentException("PR 없음: ${cmd.pullRequestId}") }

        if (pr.repository.id != repo.id) throw RepositoryMismatch(repo.id, pr.id)
        ensurePerm(repo.id, requester.id, pr.author.id)

        val openId = codes.prStatusId(PrStatus.OPEN)
        val closedId = codes.prStatusId(PrStatus.CLOSED)
        if (pr.statusCodeId != closedId) throw InvalidStateTransition("closed 상태 에서만 reopen 가능합니다.")

        pr.statusCodeId = openId
        pr.closedAt = null
        pr.updatedAt = LocalDateTime.now()
        pullRequestRepository.save(pr)

        log.info { "[PR][Reopen] pr=${pr.id} repo=${repo.id} by=${requester.id}" }
    }

    /** PR이 OPEN 상태인지 확인, 머지 가능 여부 확인 */
    @Transactional
    fun handle(cmd: MergePullRequestCommand) {
        val repo = repositoryRepository.findByIdAndIsDeletedFalse(cmd.repositoryId)
            ?: throw RepositoryNotFoundException(cmd.repositoryId)
        val requester = userRepository.findByIdAndIsDeletedFalse(cmd.requesterId)
            ?: throw IllegalArgumentException("요청자 없음: ${cmd.requesterId}")
        val pr = pullRequestRepository.findById(cmd.pullRequestId)
            .orElseThrow { IllegalArgumentException("PR 없음: ${cmd.pullRequestId}") }

        if (pr.repository.id != repo.id) throw RepositoryMismatch(repo.id, pr.id)
        ensureMaintainerPerm(repo.id, requester.id)

        val openId = codes.prStatusId(PrStatus.OPEN)
        if (pr.statusCodeId != openId) throw InvalidStateTransition("open 상태 에서만 merge 가능합니다.")
        if (!reviewCommandHandler.isMergeAllowed(pr.id)) throw MergeNotAllowed()

        // 머지 타입 파싱/매핑
        val prMergeType = PrMergeType.fromCode(cmd.mergeType) ?: throw UnsupportedMergeType(cmd.mergeType)
        val mergeTypeCodeId = codes.prMergeTypeId(prMergeType)
        val engineMergeType = codes.toGitMergeType(prMergeType)

        val targetRef = pr.targetBranch
        val sourceRef = pr.sourceBranch
        val oldHead = runCatching { gitRepositoryPort.getHeadCommitHash(repo, targetRef) }.getOrNull()
        log.info { "[PR][Merge][Start] pr=${pr.id} repo=${repo.id} $sourceRef -> $targetRef by=${requester.id} oldHead=${oldHead ?: "NA"} type=${prMergeType.code}" }

        val newHead = gitRepositoryPort.merge(
            MergeRequest(
                repository = repo,
                sourceRef = sourceRef,
                targetRef = targetRef,
                mergeType = engineMergeType,
                authorName = requester.name ?: "unknown",
                authorEmail = requester.email,
                message = cmd.message
            )
        )
        log.info { "[PR][Merge][GitDone] pr=${pr.id} repo=${repo.id} newHead=$newHead" }

        // 브랜치 메타/이벤트/인덱스 캐시 정리
        gitRepositorySyncHandler.handle(
            SyncBranchEvent(
                repositoryId = repo.id,
                branchName = targetRef,
                newHeadCommit = newHead,
                lastCommitAtUtc = null
            ),
            creator = requester
        )
        gitEventPublisher.publish(
            GitEvent(
                eventType = "PUSH",
                repositoryId = repo.id,
                ownerId = repo.owner.id,
                name = repo.name,
                branch = targetRef,
                oldrev = oldHead ?: EMPTY_SHA1,
                newrev = newHead
            )
        )
        gitIndexEvictor.evictAllOfRepository(repo.id)

        // 상태/머지 타입 저장
        val mergedId = codes.prStatusId(PrStatus.MERGED)
        pr.statusCodeId = mergedId
        pr.mergeTypeCodeId = mergeTypeCodeId
        pr.mergedBy = requester
        pr.mergedAt = LocalDateTime.now()
        pr.updatedAt = pr.mergedAt
        pullRequestRepository.save(pr)

        mergeLogRepository.save(
            PullRequestMergeLog(
                pullRequest = pr,
                mergedBy = requester,
                mergeCommitHash = newHead,
                mergeTypeCodeId = mergeTypeCodeId,
                mergedAt = pr.mergedAt!!
            )
        )

        log.info { "[PR][Merge][Done] pr=${pr.id} repo=${repo.id} type=${prMergeType.code} old=${oldHead ?: "NA"} new=$newHead by=${requester.id}" }
    }
}
