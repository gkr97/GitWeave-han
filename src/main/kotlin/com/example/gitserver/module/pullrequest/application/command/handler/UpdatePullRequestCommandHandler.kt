// module/pullrequest/application/command/handler/UpdatePullRequestCommandHandler.kt
package com.example.gitserver.module.pullrequest.application.command.handler

import com.example.gitserver.module.common.service.CommonCodeCacheService
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestRepository
import com.example.gitserver.module.repository.exception.RepositoryAccessDeniedException
import com.example.gitserver.module.repository.exception.RepositoryNotFoundException
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import com.example.gitserver.module.pullrequest.application.command.UpdatePullRequestCommand
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class UpdatePullRequestCommandHandler(
    private val repositoryRepository: RepositoryRepository,
    private val collaboratorRepository: CollaboratorRepository,
    private val userRepository: UserRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val commonCodeCacheService: CommonCodeCacheService
) {
    private val log = KotlinLogging.logger {}

    @Transactional
    fun handle(cmd: UpdatePullRequestCommand) {
        val repo = repositoryRepository.findByIdAndIsDeletedFalse(cmd.repositoryId)
            ?: throw RepositoryNotFoundException(cmd.repositoryId)

        val requester = userRepository.findByIdAndIsDeletedFalse(cmd.requesterId)
            ?: throw IllegalArgumentException("요청자 없음: ${cmd.requesterId}")

        val pr = pullRequestRepository.findById(cmd.pullRequestId)
            .orElseThrow { IllegalArgumentException("PR 없음: ${cmd.pullRequestId}") }

        if (pr.repository.id != repo.id) {
            throw IllegalArgumentException("PR이 저장소에 속하지 않습니다. repoId=${repo.id}, prId=${pr.id}")
        }

        val isOwner = (repo.owner.id == requester.id)
        val isAuthor = (pr.author.id == requester.id)
        val isCollaborator = collaboratorRepository
            .existsByRepositoryIdAndUserId(repo.id, requester.id)
        if (!isOwner && !isAuthor && !isCollaborator) {
            throw RepositoryAccessDeniedException(repo.id, requester.id)
        }

        val openStatusId = commonCodeCacheService.getCodeDetailsOrLoad("PR_STATUS")
            .firstOrNull { it.code.equals("open", ignoreCase = true) }?.id
            ?: throw IllegalStateException("PR_STATUS.open 코드 미정의")

        if (pr.statusCodeId != openStatusId) {
            throw IllegalStateException("닫힌/병합된 PR은 수정할 수 없습니다. prId=${pr.id}")
        }

        if (cmd.title == null && cmd.description == null) {
            log.info { "[PR][Update] 변경 항목 없음 prId=${pr.id}" }
            return
        }

        cmd.title?.let { t ->
            val title = t.trim()
            require(title.isNotEmpty()) { "제목은 빈 값일 수 없습니다." }
            require(title.length <= 255) { "제목은 255자를 초과할 수 없습니다." }
            pr.title = title
        }

        cmd.description?.let { d ->
            pr.description = d
        }

        pr.updatedAt = LocalDateTime.now()
        pullRequestRepository.save(pr)
        log.info { "[PR][Update] prId=${pr.id} titleUpdated=${cmd.title != null} descUpdated=${cmd.description != null}" }
    }
}
