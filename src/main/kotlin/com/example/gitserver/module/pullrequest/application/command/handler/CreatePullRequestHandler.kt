package com.example.gitserver.module.pullrequest.application.command.handler

import com.example.gitserver.common.util.GitRefUtils
import com.example.gitserver.module.pullrequest.application.command.CreatePullRequestCommand
import com.example.gitserver.module.pullrequest.domain.*
import com.example.gitserver.module.pullrequest.domain.event.PullRequestCreated
import com.example.gitserver.module.pullrequest.exception.BranchNotFound
import com.example.gitserver.module.pullrequest.exception.DuplicateOpenPr
import com.example.gitserver.module.pullrequest.exception.PermissionDenied
import com.example.gitserver.module.pullrequest.exception.SameBranchNotAllowed
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestRepository
import com.example.gitserver.module.repository.exception.RepositoryNotFoundException
import com.example.gitserver.module.repository.exception.UserNotFoundException
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import mu.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreatePullRequestHandler(
    private val repositoryRepository: RepositoryRepository,
    private val userRepository: UserRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val codes: CodeBook,
    private val policy: GitRepoPolicy,
    private val events: ApplicationEventPublisher
) {
    private val log = KotlinLogging.logger {}

    /**
     * PR 생성 요청을 받아서 DB에 저장한다.
     */
   @Transactional
    fun handle(cmd: CreatePullRequestCommand): Long {
        val repo = repositoryRepository.findByIdAndIsDeletedFalse(cmd.repositoryId)
            ?: throw RepositoryNotFoundException(cmd.repositoryId)
        val author = userRepository.findByIdAndIsDeletedFalse(cmd.authorId)
            ?: throw UserNotFoundException(cmd.authorId)

        if (!policy.isCollaboratorOrOwner(repo.id, author.id)) throw PermissionDenied()

        val sourceFull = GitRefUtils.toFullRef(cmd.sourceBranch)
        val targetFull = GitRefUtils.toFullRef(cmd.targetBranch)
        if (sourceFull.equals(targetFull, true)) throw SameBranchNotAllowed()
        if (!policy.branchExists(repo.id, sourceFull)) throw BranchNotFound(sourceFull)
        if (!policy.branchExists(repo.id, targetFull)) throw BranchNotFound(targetFull)

        val openId = codes.prStatusId(PrStatus.OPEN)
        val duplicate = pullRequestRepository
            .existsByRepositoryIdAndSourceBranchAndTargetBranchAndStatusCodeId(repo.id, sourceFull, targetFull, openId)
        if (duplicate) throw DuplicateOpenPr()

        val base = policy.getHeadCommitHash(repo.id, targetFull)
        val head = policy.getHeadCommitHash(repo.id, sourceFull)

        val pr = PullRequest(
            repository = repo,
            author = author,
            title = cmd.title,
            description = cmd.description,
            statusCodeId = openId,
            mergeTypeCodeId = null,
            mergedBy = null,
            mergedAt = null,
            closedAt = null,
            targetBranch = targetFull,
            sourceBranch = sourceFull,
            baseCommitHash = base,
            headCommitHash = head
        )

        val saved = pullRequestRepository.save(pr)
        log.info { "[PR][Create] id=${saved.id} repo=${repo.id} $sourceFull -> $targetFull base=$base head=$head" }

        events.publishEvent(PullRequestCreated(saved.id, repo.id, base, head))
        return saved.id
    }
}
