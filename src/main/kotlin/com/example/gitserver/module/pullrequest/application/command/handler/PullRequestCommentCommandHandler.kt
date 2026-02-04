package com.example.gitserver.module.pullrequest.application.command.handler

import com.example.gitserver.module.pullrequest.application.command.CreatePullRequestCommentCommand
import com.example.gitserver.module.pullrequest.application.command.DeletePullRequestCommentCommand
import com.example.gitserver.module.pullrequest.domain.PullRequestComment
import com.example.gitserver.module.pullrequest.domain.event.PullRequestCommentCreated
import com.example.gitserver.module.pullrequest.domain.event.PullRequestCommentDeleted
import com.example.gitserver.module.pullrequest.exception.PermissionDenied
import com.example.gitserver.module.pullrequest.exception.RepositoryMismatch
import com.example.gitserver.module.pullrequest.exception.PullRequestNotFoundException
import com.example.gitserver.module.pullrequest.exception.CommentNotFound
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestCommentRepository
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestRepository
import com.example.gitserver.module.repository.exception.RepositoryNotFoundException
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import com.example.gitserver.module.user.exception.UserNotFoundException
import com.example.gitserver.common.util.LogContext
import mu.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PullRequestCommentCommandHandler(
    private val repositoryRepository: RepositoryRepository,
    private val collaboratorRepository: CollaboratorRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val userRepository: UserRepository,
    private val commentRepository: PullRequestCommentRepository,
    private val events: ApplicationEventPublisher
) {
    private val log = KotlinLogging.logger {}

    /** 코멘트 생성 */
    @Transactional
    fun handle(cmd: CreatePullRequestCommentCommand): Long {
        val repo = repositoryRepository.findByIdAndIsDeletedFalse(cmd.repositoryId)
            ?: throw RepositoryNotFoundException(cmd.repositoryId)
        val pr = pullRequestRepository.findById(cmd.pullRequestId)
            .orElseThrow { PullRequestNotFoundException(cmd.pullRequestId) }
        if (pr.repository.id != repo.id) throw RepositoryMismatch(repo.id, pr.id)

        val author = userRepository.findByIdAndIsDeletedFalse(cmd.authorId)
            ?: throw UserNotFoundException(cmd.authorId)

        val owner = repo.owner.id == author.id
        val collab = collaboratorRepository.existsByRepositoryIdAndUserId(repo.id, author.id)
        val prAuthor = pr.author.id == author.id
        if (!(owner || collab || prAuthor)) throw PermissionDenied()

        val type = cmd.commentType.lowercase()
        if (type !in setOf("general", "review", "inline")) {
            throw IllegalArgumentException("허용되지 않는 commentType: ${cmd.commentType}")
        }
        if (type == "inline" || type == "review") {
            require(!cmd.filePath.isNullOrBlank()) { "inline/review 코멘트는 filePath가 필요합니다." }
            // Path Traversal 방어
            com.example.gitserver.common.util.PathSecurityUtils.sanitizePath(cmd.filePath!!)
        }

        val entity = PullRequestComment(
            pullRequest = pr,
            author = author,
            content = cmd.content,
            filePath = cmd.filePath?.let { com.example.gitserver.common.util.PathSecurityUtils.sanitizePath(it) },
            lineNumber = cmd.lineNumber,
            commentType = type
        )
        val saved = commentRepository.save(entity)

        LogContext.with(
            "eventType" to "PR_COMMENT_CREATED",
            "repoId" to repo.id.toString(),
            "prId" to pr.id.toString()
        ) {
            events.publishEvent(PullRequestCommentCreated(pr.id, saved.id, type))
        }

        log.info { "[PR][Comment][Create] pr=${pr.id} by=${author.id} commentId=${saved.id}" }
        return saved.id
    }

    /** 코멘트 삭제 */
    @Transactional
    fun handle(cmd: DeletePullRequestCommentCommand) {
        val repo = repositoryRepository.findByIdAndIsDeletedFalse(cmd.repositoryId)
            ?: throw RepositoryNotFoundException(cmd.repositoryId)
        val pr = pullRequestRepository.findById(cmd.pullRequestId)
            .orElseThrow { PullRequestNotFoundException(cmd.pullRequestId) }
        if (pr.repository.id != repo.id) throw RepositoryMismatch(repo.id, pr.id)

        val requester = userRepository.findByIdAndIsDeletedFalse(cmd.requesterId)
            ?: throw UserNotFoundException(cmd.requesterId)
        val comment = commentRepository.findById(cmd.commentId)
            .orElseThrow { CommentNotFound(cmd.commentId) }

        val owner = repo.owner.id == requester.id
        val authorOfComment = comment.author.id == requester.id
        if (!(owner || authorOfComment)) throw PermissionDenied()

        commentRepository.delete(comment)
        LogContext.with(
            "eventType" to "PR_COMMENT_DELETED",
            "repoId" to repo.id.toString(),
            "prId" to pr.id.toString()
        ) {
            events.publishEvent(PullRequestCommentDeleted(pr.id, comment.id))
        }
        log.info { "[PR][Comment][Delete] pr=${pr.id} by=${requester.id} commentId=${comment.id}" }
    }
}
