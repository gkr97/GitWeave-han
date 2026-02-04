package com.example.gitserver.module.repository.application.service

import com.example.gitserver.module.gitindex.shared.domain.port.GitRepositoryPort
import com.example.gitserver.module.repository.application.command.CreateRepositoryCommand
import com.example.gitserver.module.repository.application.command.handler.GitRepositorySyncHandler
import com.example.gitserver.module.repository.domain.Repository
import com.example.gitserver.module.repository.domain.event.SyncBranchEvent
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import mu.KotlinLogging
import org.springframework.stereotype.Component
import com.example.gitserver.common.util.LogContext

/**
 * 새 저장소의 Git 디렉토리 초기화와 브랜치 HEAD 동기화를 담당한다.
 * 애플리케이션 서비스가 인프라 세부사항을 몰라도 되도록 분리하였다.
 */
@Component
class RepositoryInitOrchestrator(
    private val gitRepositoryPort: GitRepositoryPort,
    private val repositoryRepository: RepositoryRepository,
    private val gitRepositorySyncHandler: GitRepositorySyncHandler
) {
    private val log = KotlinLogging.logger {}

    fun initializeAsync(repository: Repository, defaultBranchFullRef: String, command: CreateRepositoryCommand) {
        Thread.startVirtualThread(LogContext.wrap(Runnable {
            try {
                gitRepositoryPort.initEmptyGitDirectory(
                    repository,
                    initializeReadme = command.initializeReadme,
                    gitignoreTemplate = command.gitignoreTemplate,
                    licenseTemplate = command.licenseTemplate
                )
                log.info { "Git 저장소 초기화 완료: ${repository.name}" }

                syncHead(repository, defaultBranchFullRef, command)
            } catch (ex: Exception) {
                log.error(ex) { "Git 저장소 초기화 실패: ${repository.name}, 보상 트랜잭션 수행" }
                compensate(repository)
            }
        }))
    }

    private fun syncHead(repository: Repository, defaultBranchFullRef: String, command: CreateRepositoryCommand) {
        try {
            val headCommitHash = gitRepositoryPort.getHeadCommitHash(repository, defaultBranchFullRef)
            gitRepositorySyncHandler.handle(
                SyncBranchEvent(
                    repositoryId = repository.id,
                    branchName = command.defaultBranch,
                    newHeadCommit = headCommitHash,
                    lastCommitAtUtc = null
                ),
                creator = command.owner
            )
            log.info { "브랜치 HEAD 업데이트 완료: ${command.defaultBranch}, HEAD=$headCommitHash" }
        } catch (syncEx: Exception) {
            log.error(syncEx) { "브랜치 HEAD 업데이트 실패: ${command.defaultBranch}" }
        }
    }

    private fun compensate(repository: Repository) {
        try {
            repositoryRepository.deleteById(repository.id)
            gitRepositoryPort.deleteGitDirectories(repository)
            log.warn { "저장소 보상 삭제 완료: id=${repository.id}" }
        } catch (cleanupEx: Exception) {
            log.error(cleanupEx) { "저장소 보상 삭제 실패: id=${repository.id}" }
        }
    }
}
