package com.example.gitserver.module.repository.application.command.handler

import com.example.gitserver.module.gitindex.application.service.GitService
import com.example.gitserver.module.repository.application.command.UpdateRepositoryCommand
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Service
class UpdateRepositoryCommandHandler(
    private val repositoryRepository: RepositoryRepository,
    private val gitService: GitService
) {
    private val log = KotlinLogging.logger {}

    /**
     * 저장소 업데이트 명령을 처리합니다.
     * - 요청자가 저장소 소유자인지 확인
     * - 저장소 이름 중복 검사
     * - 저장소 정보와 실제 디렉터리 이름 업데이트
     *
     * @param command 저장소 업데이트 명령
     */
    @Transactional
    fun handle(command: UpdateRepositoryCommand) {
        val repo = repositoryRepository.findByIdAndIsDeletedFalse(command.repositoryId)
            ?: throw IllegalArgumentException("존재하지 않는 레포지터리입니다.")

        if (repo.owner.id != command.requesterId) {
            log.warn { "수정 권한 없음: userId=${command.requesterId}, repoId=${command.repositoryId}" }
            throw IllegalAccessException("수정 권한이 없습니다.")
        }

        val isDuplicate = repositoryRepository.existsByOwnerIdAndNameAndIdNot(
            ownerId = command.requesterId,
            name = command.newName,
            exceptId = repo.id
        )
        if (isDuplicate) {
            log.warn { "중복 이름: userId=${command.requesterId}, repoName=${command.newName}" }
            throw IllegalArgumentException("이미 동일한 이름의 레포지터리가 존재합니다.")
        }

        val oldName = repo.name
        val isNameChanged = oldName != command.newName

        repo.name = command.newName
        repo.description = command.newDescription

        repositoryRepository.save(repo)
        log.info { "레포지터리 정보 수정 완료: repoId=${repo.id}, name='${oldName}'→'${repo.name}'" }

        if (isNameChanged) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    try {
                        gitService.renameRepositoryDirectory(repo.owner.id, oldName, repo.name)
                        log.info { "레포 디렉터리 이름 변경 성공: ownerId=${repo.owner.id}, $oldName → ${repo.name}" }
                    } catch (e: Exception) {
                        log.error(e) { "레포 디렉터리 이름 변경 실패: ownerId=${repo.owner.id}, old=$oldName, new=${repo.name}" }
                        // TODO: 장애 처리 추가 예정
                    }
                }
            })
        }
    }
}
