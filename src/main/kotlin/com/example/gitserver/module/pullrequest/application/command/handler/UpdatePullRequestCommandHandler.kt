package com.example.gitserver.module.pullrequest.application.command.handler

import com.example.gitserver.module.pullrequest.application.command.UpdatePullRequestCommand
import com.example.gitserver.module.pullrequest.domain.CodeBook
import com.example.gitserver.module.pullrequest.domain.PrStatus
import com.example.gitserver.module.pullrequest.exception.InvalidStateTransition
import com.example.gitserver.module.pullrequest.exception.PermissionDenied
import com.example.gitserver.module.pullrequest.exception.RepositoryMismatch
import com.example.gitserver.module.repository.exception.RepositoryNotFoundException
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestRepository
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import com.example.gitserver.module.user.exception.UserNotFoundException
import mu.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class UpdatePullRequestCommandHandler(
    private val repositoryRepository: RepositoryRepository,
    private val collaboratorRepository: CollaboratorRepository,
    private val userRepository: UserRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val codes: CodeBook,
    private val events: ApplicationEventPublisher
) {
    private val log = KotlinLogging.logger {}


    /** PR 수정 권한 체크 */
    @Transactional
    fun handle(cmd: UpdatePullRequestCommand) {
        val repo = repositoryRepository.findByIdAndIsDeletedFalse(cmd.repositoryId)
            ?: throw RepositoryNotFoundException(cmd.repositoryId)
        val requester = userRepository.findByIdAndIsDeletedFalse(cmd.requesterId)
            ?: throw UserNotFoundException(cmd.requesterId)
        val pr = pullRequestRepository.findById(cmd.pullRequestId)
            .orElseThrow { IllegalArgumentException("PR 없음: ${cmd.pullRequestId}") }

        if (pr.repository.id != repo.id) throw RepositoryMismatch(repo.id, pr.id)

        val owner = repo.owner.id == requester.id
        val author = pr.author.id == requester.id
        val collaborator = collaboratorRepository.existsByRepositoryIdAndUserId(repo.id, requester.id)
        if (!(owner || author || collaborator)) throw PermissionDenied()

        val openId = codes.prStatusId(PrStatus.OPEN)
        if (pr.statusCodeId != openId) throw InvalidStateTransition("닫힌/병합된 PR은 수정 불가")

        if (cmd.title == null && cmd.description == null) {
            log.info { "[PR][Update] 변경 없음 pr=${pr.id}" }
            return
        }

        cmd.title?.let {
            val t = it.trim()
            require(t.isNotEmpty()) { "제목은 비어있을 수 없습니다." }
            require(t.length <= 255) { "제목은 255자를 초과할 수 없습니다." }
            pr.title = t
        }
        cmd.description?.let { pr.description = it }

        pr.updatedAt = LocalDateTime.now()
        pullRequestRepository.save(pr)

        log.info { "[PR][Update] done pr=${pr.id} title=${cmd.title!=null} desc=${cmd.description!=null}" }
    }
}
