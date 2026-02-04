package com.example.gitserver.module.repository.application.command.handler

import com.example.gitserver.common.cache.RepoCacheEvictor
import com.example.gitserver.common.cache.registerRepoCacheEvictionAfterCommit
import com.example.gitserver.module.repository.application.command.ChangeRepositoryVisibilityCommand
import com.example.gitserver.module.repository.domain.CodeBook
import com.example.gitserver.module.repository.domain.event.RepositoryVisibilityChanged
import com.example.gitserver.module.repository.domain.policy.RepoAccessPolicy
import com.example.gitserver.module.repository.exception.RepositoryNotFoundException
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.common.util.LogContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ChangeRepoVisibilityCommandHandler(
    private val repositoryRepository: RepositoryRepository,
    private val access: RepoAccessPolicy,
    private val codeBook: CodeBook,
    private val evictor: RepoCacheEvictor,
    private val events: ApplicationEventPublisher
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
            ?: throw RepositoryNotFoundException(command.repositoryId)

        if (!access.isOwner(repo.id, command.requesterId))
            throw SecurityException("수정 권한이 없습니다.")

        repo.visibilityCodeId = codeBook.visibilityId(command.newVisibility)

        LogContext.with(
            "eventType" to "REPO_VISIBILITY_CHANGED",
            "repoId" to repo.id.toString()
        ) {
            events.publishEvent(RepositoryVisibilityChanged(repo.id, command.newVisibility))
        }
        registerRepoCacheEvictionAfterCommit(evictor, evictAll = true)
    }
}
