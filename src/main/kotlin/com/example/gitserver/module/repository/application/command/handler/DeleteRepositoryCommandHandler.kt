package com.example.gitserver.module.repository.application.command.handler

import com.example.gitserver.module.repository.application.command.DeleteRepositoryCommand
import com.example.gitserver.module.repository.exception.NotRepositoryOwnerException
import com.example.gitserver.module.repository.exception.RepositoryNotFoundException
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class DeleteRepositoryCommandHandler(
    private val repositoryRepository: RepositoryRepository,
    private val userRepository: UserRepository
) {
    private val log = KotlinLogging.logger {}

    /**
     * 저장소 삭제 명령을 처리합니다.
     * - 요청자가 저장소 소유자인지 확인
     * - 저장소가 이미 삭제된 상태인지 확인
     * - 저장소를 삭제 상태로 변경
     *
     * @param command 저장소 삭제 명령
     */
    @Transactional
    fun handle(command: DeleteRepositoryCommand) {
        log.info { "[Repo-Delete] 삭제 요청: repoId=${command.repositoryId}, by=${command.requesterEmail}" }

        val user = userRepository.findByEmailAndIsDeletedFalse(command.requesterEmail)
            ?: throw IllegalArgumentException("인증된 사용자가 없음: ${command.requesterEmail}")

        val repo = repositoryRepository.findById(command.repositoryId)
            .orElseThrow { RepositoryNotFoundException(command.repositoryId) }

        if (repo.owner.id != user.id) {
            log.warn { "[Repo-Delete] 소유자 아님: repoId=${repo.id}, ownerId=${repo.owner.id}, req=${user.id}" }
            throw NotRepositoryOwnerException()
        }

        if (repo.isDeleted) {
            log.warn { "[Repo-Delete] 이미 삭제됨: repoId=${repo.id}" }
            throw IllegalStateException("이미 삭제된 저장소입니다.")
        }

        repo.isDeleted = true
        repo.updatedAt = LocalDateTime.now()
        repositoryRepository.save(repo)
        log.info { "[Repo-Delete] 삭제 완료: repoId=${repo.id}, by=${user.id}" }

    }
}
