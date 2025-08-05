package com.example.gitserver.module.repository.application.command.handler

import com.example.gitserver.module.common.service.CommonCodeCacheService
import com.example.gitserver.module.repository.application.command.ChangeRepositoryVisibilityCommand
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ChangeRepoVisibilityCommandHandler (
    private val repositoryRepository: RepositoryRepository,
    private val commonCodeCacheService: CommonCodeCacheService
) {

    /**
     * 레포지터리 가시성 변경 명령을 처리합니다.
     * - 요청자가 레포지터리 소유자인지 확인
     * - 레포지터리 가시성 코드 ID를 가져와서 업데이트
     *
     * @param command 레포지터리 가시성 변경 명령
     */
    @Transactional
    fun handle(command: ChangeRepositoryVisibilityCommand) {
        val repo = repositoryRepository.findByIdAndIsDeletedFalse(command.repositoryId)
            ?: throw IllegalArgumentException("레포지터리를 찾을 수 없습니다.")

        if (repo.owner.id != command.requesterId)
            throw SecurityException("수정 권한이 없습니다.")

        val visibilityCodeId = commonCodeCacheService.getVisibilityCodeId(command.newVisibility)
        repo.visibilityCodeId = visibilityCodeId
    }
}
