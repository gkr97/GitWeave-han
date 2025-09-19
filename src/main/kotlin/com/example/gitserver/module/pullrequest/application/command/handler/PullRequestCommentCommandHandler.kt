package com.example.gitserver.module.pullrequest.application.command.handler

import com.example.gitserver.module.pullrequest.application.command.CreatePullRequestCommentCommand
import com.example.gitserver.module.pullrequest.application.command.DeletePullRequestCommentCommand
import com.example.gitserver.module.pullrequest.domain.PullRequestComment
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestCommentRepository
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestRepository
import com.example.gitserver.module.repository.exception.RepositoryAccessDeniedException
import com.example.gitserver.module.repository.exception.RepositoryNotFoundException
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PullRequestCommentCommandHandler(
    private val repositoryRepository: RepositoryRepository,
    private val collaboratorRepository: CollaboratorRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val userRepository: UserRepository,
    private val commentRepository: PullRequestCommentRepository
) {
    private val log = KotlinLogging.logger {}

    /**
     * PR 코멘트 생성
     * - 권한 확인(소유자/협업자/PR작성자)
     * - commentType 검증
     * - inline/review면 filePath 필수
     */
    @Transactional
    fun handle(cmd: CreatePullRequestCommentCommand): Long {
        val repo = repositoryRepository.findByIdAndIsDeletedFalse(cmd.repositoryId)
            ?: throw RepositoryNotFoundException(cmd.repositoryId)

        val pr = pullRequestRepository.findById(cmd.pullRequestId)
            .orElseThrow { IllegalArgumentException("PR 없음: ${cmd.pullRequestId}") }
        require(pr.repository.id == repo.id) { "PR이 저장소에 속하지 않습니다." }

        val author = userRepository.findByIdAndIsDeletedFalse(cmd.authorId)
            ?: throw IllegalArgumentException("작성자 없음: ${cmd.authorId}")

        val isOwner = repo.owner.id == author.id
        val isCollaborator = collaboratorRepository.existsByRepositoryIdAndUserId(repo.id, author.id)
        val isAuthorOfPr = pr.author.id == author.id
        if (!(isOwner || isCollaborator || isAuthorOfPr)) {
            throw RepositoryAccessDeniedException(repo.id, author.id)
        }

        val type = cmd.commentType.lowercase()
        if (type == "inline" || type == "review") {
            require(!cmd.filePath.isNullOrBlank()) { "inline/review 코멘트는 filePath가 필요합니다." }
        }
        if (type !in setOf("general", "review", "inline")) {
            throw IllegalArgumentException("허용되지 않는 commentType: ${cmd.commentType}")
        }

        val entity = PullRequestComment(
            pullRequest = pr,
            author = author,
            content = cmd.content,
            filePath = cmd.filePath,
            lineNumber = cmd.lineNumber,
            commentType = type
        )
        val saved = commentRepository.save(entity)
        log.info { "[PR][Comment][Create] prId=${pr.id} by=${author.id} commentId=${saved.id}" }
        return saved.id
    }

    @Transactional
    fun handle(cmd: DeletePullRequestCommentCommand) {
        val repo = repositoryRepository.findByIdAndIsDeletedFalse(cmd.repositoryId)
            ?: throw RepositoryNotFoundException(cmd.repositoryId)

        val pr = pullRequestRepository.findById(cmd.pullRequestId)
            .orElseThrow { IllegalArgumentException("PR 없음: ${cmd.pullRequestId}") }
        require(pr.repository.id == repo.id) { "PR이 저장소에 속하지 않습니다." }

        val requester = userRepository.findByIdAndIsDeletedFalse(cmd.requesterId)
            ?: throw IllegalArgumentException("요청자 없음: ${cmd.requesterId}")

        val comment = commentRepository.findById(cmd.commentId)
            .orElseThrow { IllegalArgumentException("코멘트 없음: ${cmd.commentId}") }

        val isOwner = repo.owner.id == requester.id
        val isAuthorOfComment = comment.author.id == requester.id
        if (!(isOwner || isAuthorOfComment)) {
            throw RepositoryAccessDeniedException(repo.id, requester.id)
        }

        commentRepository.delete(comment)
        log.info { "[PR][Comment][Delete] prId=${pr.id} by=${requester.id} commentId=${comment.id}" }
    }
}
