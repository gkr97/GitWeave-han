package com.example.gitserver.module.repository.application.command.handler

import com.example.gitserver.module.gitindex.domain.port.GitRepositoryPort
import com.example.gitserver.module.repository.application.command.UpdateRepositoryCommand
import com.example.gitserver.module.repository.domain.event.RepositoryRenamed
import com.example.gitserver.module.repository.domain.policy.RepoAccessPolicy
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import mu.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Service
class UpdateRepositoryCommandHandler(
    private val repositoryRepository: RepositoryRepository,
    private val gitRepositoryPort: GitRepositoryPort,
    private val access: RepoAccessPolicy,
    private val events: ApplicationEventPublisher
) {
    private val log = KotlinLogging.logger {}

    /**
     * 업데이트 명령을 처리합니다.
     * - 이름 변경
     * - 설명 변경
     * - 권한 변경
     * - Git 디렉토리 이름 변경
     */
    @Transactional
    fun handle(command: UpdateRepositoryCommand) {
        val repo = repositoryRepository.findByIdAndIsDeletedFalse(command.repositoryId)
            ?: throw com.example.gitserver.module.repository.exception.RepositoryNotFoundException(command.repositoryId)

        if (!access.isOwner(repo.id, command.requesterId)) {
            log.warn { "수정 권한 없음: userId=${command.requesterId}, repoId=${command.repositoryId}" }
            throw IllegalAccessException("수정 권한이 없습니다.")
        }

        val isDuplicate = repositoryRepository.existsByOwnerIdAndNameAndIdNot(
            ownerId = command.requesterId, name = command.newName, exceptId = repo.id
        )
        if (isDuplicate) throw com.example.gitserver.module.repository.exception.DuplicateRepositoryNameException(command.newName)

        val oldName = repo.name
        val isNameChanged = oldName != command.newName

        repo.name = command.newName
        repo.description = command.newDescription
        repositoryRepository.save(repo)

        if (isNameChanged) {
            events.publishEvent(RepositoryRenamed(repo.id, oldName, repo.name))
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    try {
                        gitRepositoryPort.renameRepositoryDirectory(repo.owner.id, oldName, repo.name)
                        log.info { "레포 디렉터리 이름 변경 성공: ownerId=${repo.owner.id}, $oldName → ${repo.name}" }
                    } catch (e: Exception) {
                        log.error(e) { "레포 디렉터리 이름 변경 실패: ownerId=${repo.owner.id}, old=$oldName, new=${repo.name}" }
                    }
                }
            })
        }
    }
}
