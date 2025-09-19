package com.example.gitserver.module.pullrequest.application.command.handler

import com.example.gitserver.module.common.service.CommonCodeCacheService
import com.example.gitserver.module.gitindex.domain.dto.MergeRequest
import com.example.gitserver.module.gitindex.domain.event.GitEvent
import com.example.gitserver.module.gitindex.domain.port.GitRepositoryPort
import com.example.gitserver.module.gitindex.domain.vo.MergeType
import com.example.gitserver.module.gitindex.infrastructure.redis.GitIndexEvictor
import com.example.gitserver.module.pullrequest.application.MergePullRequestCommand
import com.example.gitserver.module.pullrequest.application.command.ClosePullRequestCommand
import com.example.gitserver.module.pullrequest.application.command.ReopenPullRequestCommand
import com.example.gitserver.module.pullrequest.domain.PullRequestMergeLog
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestMergeLogRepository
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestRepository
import com.example.gitserver.module.repository.application.command.handler.GitRepositorySyncHandler
import com.example.gitserver.module.repository.domain.event.GitEventPublisher
import com.example.gitserver.module.repository.domain.event.SyncBranchEvent
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
class PullRequestStateCommandHandler(
    private val repositoryRepository: RepositoryRepository,
    private val collaboratorRepository: CollaboratorRepository,
    private val userRepository: UserRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val mergeLogRepository: PullRequestMergeLogRepository,
    private val commonCodeCacheService: CommonCodeCacheService,
    private val reviewCommandHandler: PullRequestReviewCommandHandler,
    private val gitRepositoryPort: GitRepositoryPort,
    private val gitRepositorySyncHandler: GitRepositorySyncHandler,
    private val gitEventPublisher: GitEventPublisher,
    private val gitIndexEvictor: GitIndexEvictor,
) {
    private val log = KotlinLogging.logger {}

    companion object { private const val EMPTY_SHA1 = "0000000000000000000000000000000000000000" }

    private fun statusId(code: String): Long =
        commonCodeCacheService.getCodeDetailsOrLoad("PR_STATUS")
            .firstOrNull { it.code.equals(code, ignoreCase = true) }
            ?.id ?: error("PR_STATUS.$code 미정의")

    private fun mergeTypeId(code: String): Long? =
        commonCodeCacheService.getCodeDetailsOrLoad("PR_MERGE_TYPE")
            .firstOrNull { it.code.equals(code, ignoreCase = true) }
            ?.id

    private fun toMergeType(code: String): MergeType = when (code.lowercase()) {
        "merge_commit" -> MergeType.MERGE_COMMIT
        "squash" -> MergeType.SQUASH
        "rebase" -> MergeType.REBASE
        else -> error("지원하지 않는 mergeType: $code")
    }

    @Transactional
    fun handle(cmd: ClosePullRequestCommand) {
        val repo = repositoryRepository.findByIdAndIsDeletedFalse(cmd.repositoryId)
            ?: throw RepositoryNotFoundException(cmd.repositoryId)
        val requester = userRepository.findByIdAndIsDeletedFalse(cmd.requesterId)
            ?: throw IllegalArgumentException("요청자 없음: ${cmd.requesterId}")
        val pr = pullRequestRepository.findById(cmd.pullRequestId)
            .orElseThrow { IllegalArgumentException("PR 없음: ${cmd.pullRequestId}") }

        require(pr.repository.id == repo.id) { "PR이 저장소에 속하지 않습니다." }

        val isOwner = (repo.owner.id == requester.id)
        val isCollaborator = collaboratorRepository.existsByRepositoryIdAndUserId(repo.id, requester.id)
        val isAuthor = (pr.author.id == requester.id)
        if (!(isOwner || isCollaborator || isAuthor)) throw RepositoryAccessDeniedException(repo.id, requester.id)

        val openId = statusId("open")
        val closedId = statusId("closed")
        if (pr.statusCodeId != openId) throw IllegalStateException("open 상태 에서만 close 가능합니다.")

        pr.statusCodeId = closedId
        pr.closedAt = LocalDateTime.now()
        pr.updatedAt = LocalDateTime.now()
        pullRequestRepository.save(pr)

        log.info { "[PR][Close] pr=${pr.id} repo=${repo.id} by=${requester.id}" }
    }

    @Transactional
    fun handle(cmd: ReopenPullRequestCommand) {
        val repo = repositoryRepository.findByIdAndIsDeletedFalse(cmd.repositoryId)
            ?: throw RepositoryNotFoundException(cmd.repositoryId)
        val requester = userRepository.findByIdAndIsDeletedFalse(cmd.requesterId)
            ?: throw IllegalArgumentException("요청자 없음: ${cmd.requesterId}")
        val pr = pullRequestRepository.findById(cmd.pullRequestId)
            .orElseThrow { IllegalArgumentException("PR 없음: ${cmd.pullRequestId}") }

        require(pr.repository.id == repo.id) { "PR이 저장소에 속하지 않습니다." }

        val isOwner = (repo.owner.id == requester.id)
        val isCollaborator = collaboratorRepository.existsByRepositoryIdAndUserId(repo.id, requester.id)
        val isAuthor = (pr.author.id == requester.id)
        if (!(isOwner || isCollaborator || isAuthor)) throw RepositoryAccessDeniedException(repo.id, requester.id)

        val openId = statusId("open")
        val closedId = statusId("closed")
        val mergedId = statusId("merged")
        if (pr.statusCodeId != closedId) throw IllegalStateException("closed 상태 에서만 reopen 가능합니다. 현재=${pr.statusCodeId}, merged=${mergedId}")

        pr.statusCodeId = openId
        pr.closedAt = null
        pr.updatedAt = LocalDateTime.now()
        pullRequestRepository.save(pr)

        log.info { "[PR][Reopen] pr=${pr.id} repo=${repo.id} by=${requester.id}" }
    }

    @Transactional
    fun handle(cmd: MergePullRequestCommand) {
        val repo = repositoryRepository.findByIdAndIsDeletedFalse(cmd.repositoryId)
            ?: throw RepositoryNotFoundException(cmd.repositoryId)
        val requester = userRepository.findByIdAndIsDeletedFalse(cmd.requesterId)
            ?: throw IllegalArgumentException("요청자 없음: ${cmd.requesterId}")
        val pr = pullRequestRepository.findById(cmd.pullRequestId)
            .orElseThrow { IllegalArgumentException("PR 없음: ${cmd.pullRequestId}") }

        require(pr.repository.id == repo.id) { "PR이 저장소에 속하지 않습니다." }

        val isOwner = (repo.owner.id == requester.id)
        val isCollaborator = collaboratorRepository.existsByRepositoryIdAndUserId(repo.id, requester.id)
        if (!(isOwner || isCollaborator)) throw RepositoryAccessDeniedException(repo.id, requester.id)

        val openId = statusId("open")
        val mergedId = statusId("merged")
        if (pr.statusCodeId != openId) throw IllegalStateException("open 상태 에서만 merge 가능합니다.")
        if (!reviewCommandHandler.isMergeAllowed(pr.id)) throw IllegalStateException("모든 리뷰어 승인 필요 혹은 변경 요청이 존재합니다.")

        val targetRef = pr.targetBranch
        val sourceRef = pr.sourceBranch
        val oldHead = runCatching { gitRepositoryPort.getHeadCommitHash(repo, targetRef) }.getOrNull()
        log.info { "[PR][Merge][Start] pr=${pr.id} repo=${repo.id} $sourceRef -> $targetRef by=${requester.id} oldHead=${oldHead ?: "NA"} type=${cmd.mergeType}" }

        val newHead = gitRepositoryPort.merge(
            MergeRequest(
                repository = repo,
                sourceRef = sourceRef,
                targetRef = targetRef,
                mergeType = toMergeType(cmd.mergeType),
                authorName = requester.name ?: "unknown",
                authorEmail = requester.email,
                message = cmd.message
            )
        )
        log.info { "[PR][Merge][GitDone] pr=${pr.id} repo=${repo.id} newHead=$newHead" }

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

        val mergeTypeCodeId = mergeTypeId(cmd.mergeType)
            ?: throw IllegalArgumentException("유효하지 않은 mergeType: ${cmd.mergeType}")

        pr.statusCodeId = mergedId
        pr.mergeTypeCodeId = mergeTypeCodeId
        pr.mergedBy = requester
        pr.mergedAt = LocalDateTime.now()
        pr.updatedAt = LocalDateTime.now()
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

        log.info { "[PR][Merge][Done] pr=${pr.id} repo=${repo.id} type=${cmd.mergeType} old=${oldHead ?: "NA"} new=$newHead by=${requester.id}" }
    }
}
