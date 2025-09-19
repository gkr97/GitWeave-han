// module/pullrequest/application/command/handler/PullRequestCommandHandler.kt
package com.example.gitserver.module.pullrequest.application.command.handler

import com.example.gitserver.common.util.GitRefUtils
import com.example.gitserver.module.common.service.CommonCodeCacheService
import com.example.gitserver.module.gitindex.domain.port.GitRepositoryPort
import com.example.gitserver.module.pullrequest.application.command.CreatePullRequestCommand
import com.example.gitserver.module.pullrequest.application.service.PullRequestCommitMappingService
import com.example.gitserver.module.pullrequest.application.service.PullRequestIndexingService
import com.example.gitserver.module.pullrequest.domain.PullRequest
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestRepository
import com.example.gitserver.module.repository.exception.*
import com.example.gitserver.module.repository.infrastructure.persistence.BranchRepository
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PullRequestCommandHandler(
    private val repositoryRepository: RepositoryRepository,
    private val collaboratorRepository: CollaboratorRepository,
    private val branchRepository: BranchRepository,
    private val userRepository: UserRepository,
    private val gitRepositoryPort: GitRepositoryPort,
    private val commonCodeCacheService: CommonCodeCacheService,
    private val pullRequestIndexingService: PullRequestIndexingService,
    private val pullRequestRepository: PullRequestRepository,
    private val commitMappingService: PullRequestCommitMappingService,
) {
    private val log = KotlinLogging.logger {}

    /**
     * PR 생성
     * - 권한 확인(소유자/협업자)
     * - 브랜치 존재/동일 금지
     * - 중복 오픈 PR 방지
     * - base/head 커밋 스냅샷 저장
     * - 상태=open
     */
    @Transactional
    fun handle(cmd: CreatePullRequestCommand): Long {
        val repo = repositoryRepository.findByIdAndIsDeletedFalse(cmd.repositoryId)
            ?: throw RepositoryNotFoundException(cmd.repositoryId)

        val author = userRepository.findByIdAndIsDeletedFalse(cmd.authorId)
            ?: throw UserNotFoundException(cmd.authorId)

        val isOwner = repo.owner.id == author.id
        val isCollaborator = collaboratorRepository.existsByRepositoryIdAndUserId(cmd.repositoryId, author.id)
        if (!isOwner && !isCollaborator) {
            throw RepositoryAccessDeniedException(cmd.repositoryId, author.id)
        }

        val sourceFull = GitRefUtils.toFullRef(cmd.sourceBranch)
        val targetFull = GitRefUtils.toFullRef(cmd.targetBranch)
        val sourceShort = GitRefUtils.toShortName(sourceFull)!!
        val targetShort = GitRefUtils.toShortName(targetFull)!!

        if (sourceShort.equals(targetShort, ignoreCase = true)) {
            throw IllegalArgumentException("source와 target 브랜치는 달라야 합니다.")
        }

        val srcBranch = branchRepository.findByRepositoryIdAndName(cmd.repositoryId, sourceFull)
            ?: throw BaseBranchNotFoundException(cmd.repositoryId, sourceShort)
        val tgtBranch = branchRepository.findByRepositoryIdAndName(cmd.repositoryId, targetFull)
            ?: throw BaseBranchNotFoundException(cmd.repositoryId, targetShort)

        val openStatusId = commonCodeCacheService.getCodeDetailsOrLoad("PR_STATUS")
            .firstOrNull { it.code.equals("open", ignoreCase = true) }
            ?.id ?: throw InvalidRoleCodeException("PR_STATUS.open")

        val dup = pullRequestRepository.existsByRepositoryIdAndSourceBranchAndTargetBranchAndStatusCodeId(
            repositoryId = repo.id,
            sourceBranch = sourceFull,
            targetBranch = targetFull,
            statusCodeId = openStatusId
        )
        if (dup) {
            throw IllegalStateException("동일한 source/target의 오픈 PR이 이미 존재합니다.")
        }

        val baseHead = runCatching { gitRepositoryPort.getHeadCommitHash(repo, targetFull) }
            .getOrElse { throw HeadCommitNotFoundException(targetFull) }
        val sourceHead = runCatching { gitRepositoryPort.getHeadCommitHash(repo, sourceFull) }
            .getOrElse { throw HeadCommitNotFoundException(sourceFull) }

        val pr = PullRequest(
            repository = repo,
            author = author,
            title = cmd.title,
            description = cmd.description,
            statusCodeId = openStatusId,
            mergeTypeCodeId = null,
            mergedBy = null,
            mergedAt = null,
            closedAt = null,
            targetBranch = targetFull,
            sourceBranch = sourceFull,
            baseCommitHash = baseHead,
            headCommitHash = sourceHead
        )

        val saved = pullRequestRepository.save(pr)
        log.info { "[PR] created id=${saved.id} repo=${repo.id} ${sourceShort} -> ${targetShort} base=$baseHead head=$sourceHead" }

        pullRequestIndexingService.reindex(
            prId = saved.id,
            repoId = repo.id,
            base = baseHead,
            head = sourceHead
        )

        commitMappingService.refresh(
            prId = saved.id,
            repoId = repo.id,
            base = baseHead,
            head = sourceHead
        )

        return saved.id
    }
}
