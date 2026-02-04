package com.example.gitserver.module.repository.application.command.handler

import com.example.gitserver.common.util.GitRefUtils.toFullRef
import com.example.gitserver.module.common.application.service.CommonCodeCacheService
import com.example.gitserver.common.cache.RepoCacheEvictor
import com.example.gitserver.common.cache.registerRepoCacheEvictionAfterCommitForRepo
import com.example.gitserver.module.repository.application.command.CreateRepositoryCommand
import com.example.gitserver.module.repository.application.service.RepositoryInitOrchestrator
import com.example.gitserver.module.repository.domain.Branch
import com.example.gitserver.module.repository.domain.Collaborator
import com.example.gitserver.module.repository.domain.Repository
import com.example.gitserver.module.repository.domain.event.RepositoryCreated
import com.example.gitserver.module.repository.exception.*
import com.example.gitserver.module.repository.infrastructure.persistence.BranchRepository
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import com.example.gitserver.common.util.LogContext
import com.example.gitserver.module.gitindex.storage.application.routing.RepoPlacementService
import com.example.gitserver.module.gitindex.storage.infrastructure.persistence.GitNodeRepository
import com.example.gitserver.module.gitindex.storage.infrastructure.persistence.RepoLocationRepository
import com.example.gitserver.module.gitindex.storage.infrastructure.persistence.RepoReplicaRepository
import com.example.gitserver.module.gitindex.storage.domain.RepoReplicaEntity
import com.example.gitserver.module.gitindex.storage.domain.RepoReplicaId
import com.example.gitserver.module.gitindex.storage.infrastructure.routing.GitStorageRemoteClient
import com.example.gitserver.module.gitindex.storage.interfaces.GitStorageInitRequest
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Service
class CreateRepositoryCommandHandler(
    private val repositoryRepository: RepositoryRepository,
    private val branchRepository: BranchRepository,
    private val commonCodeCacheService: CommonCodeCacheService,
    private val collaboratorRepository: CollaboratorRepository,
    private val userRepository: UserRepository,
    private val evictor: RepoCacheEvictor,
    private val events: ApplicationEventPublisher,
    private val repositoryInitOrchestrator: RepositoryInitOrchestrator,
    private val repoPlacementService: RepoPlacementService,
    private val repoLocationRepository: RepoLocationRepository,
    private val gitNodeRepository: GitNodeRepository,
    private val repoReplicaRepository: RepoReplicaRepository,
    private val gitStorageRemoteClient: GitStorageRemoteClient,
    @Value("\${git.routing.local-node-id:local}") private val localNodeId: String
) {
    private val log = KotlinLogging.logger {}

    /**
     * 저장소 생성 명령을 처리합니다.
     * - 저장소 이름 중복 검사
     * - Git 디렉토리 초기화
     * - 기본 브랜치 생성
     * - 소유자 collaborator 등록
     * - 초대 collaborator 등록
     * - Git 이벤트 발행
     *
     * @param command 저장소 생성 명령
     * @return 생성된 Repository 객체
     */
    @Transactional
    fun handle(command: CreateRepositoryCommand): Repository {
        log.info { "저장소 생성 요청: owner=${command.owner.id}, name=${command.name}" }

        if (repositoryRepository.existsByOwnerIdAndNameAndIsDeletedFalse(command.owner.id, command.name)) {
            log.warn { "중복된 저장소 이름: owner=${command.owner.id}, name=${command.name}" }
            throw DuplicateRepositoryNameException(command.name)
        }

        val visibilityCodeId = commonCodeCacheService.getCodeDetailsOrLoad("VISIBILITY")
            .firstOrNull { it.code == command.visibilityCode }?.id
            ?: throw InvalidVisibilityCodeException(command.visibilityCode)

        val repository = Repository.create(
            owner = command.owner,
            name = command.name,
            description = command.description,
            visibilityCodeId = visibilityCodeId,
            defaultBranch = command.defaultBranch,
            license = command.license,
            language = command.language,
            homepageUrl = command.homepageUrl,
            topics = command.topics
        )

        repositoryRepository.saveAndFlush(repository)
        log.info { "저장소 DB 저장 완료: id=${repository.id}" }

        val defaultBranchFullRef = toFullRef(command.defaultBranch)

        // 브랜치를 먼저 생성   (headCommitHash는 Git 초기화 완료 후 업데이트)
        val branch = Branch.createDefault(repository, defaultBranchFullRef, command.owner)
        branchRepository.save(branch)
        log.info { "기본 브랜치 생성 완료: ${branch.name} (HEAD는 Git 초기화 후 업데이트됨)" }

        val roleCodeId = commonCodeCacheService.getCodeDetailsOrLoad("ROLE")
            .firstOrNull { it.code == "owner" }?.id
            ?: throw InvalidRoleCodeException("owner")

        val collaborator = Collaborator(
            repository = repository,
            user = command.owner,
            roleCodeId = roleCodeId,
            accepted = true
        )
        collaboratorRepository.save(collaborator)
        log.info { "소유자 collaborator 등록 완료: user=${command.owner.id}" }

        val invitees = command.invitedUserIds.orEmpty()
        val defaultRoleCodeId = commonCodeCacheService.getCodeDetailsOrLoad("ROLE")
            .firstOrNull { it.code == "maintainer" }?.id
            ?: throw InvalidRoleCodeException("maintainer")

        invitees.forEach { userId ->
            val user = userRepository.findById(userId).orElse(null)
                ?: throw UserNotFoundException(userId)
            if (user.id != command.owner.id) {
                val invite = Collaborator(
                    repository = repository,
                    user = user,
                    roleCodeId = defaultRoleCodeId,
                    accepted = false
                )
                collaboratorRepository.save(invite)
                log.info { "초대 collaborator 추가 완료: user=${user.id}" }
            }
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    try {
                        repoPlacementService.assignRepository(repository.id)
                        val location = repoLocationRepository.findById(repository.id).orElse(null)
                        val primaryNodeId = location?.primaryNodeId ?: localNodeId
                        val initRequest = GitStorageInitRequest(
                            repositoryId = repository.id,
                            defaultBranch = command.defaultBranch,
                            initializeReadme = command.initializeReadme,
                            gitignoreTemplate = command.gitignoreTemplate,
                            licenseTemplate = command.licenseTemplate
                        )

                        if (primaryNodeId == localNodeId) {
                            // Git 초기화 + 브랜치 동기화를 별도 오케스트레이터에 위임
                            repositoryInitOrchestrator.initializeAsync(repository, defaultBranchFullRef, command)
                        } else {
                            val primaryNode = gitNodeRepository.findById(primaryNodeId).orElse(null)
                            val okPrimary = primaryNode?.let { gitStorageRemoteClient.initRepository(it.host, initRequest) } ?: false
                            if (okPrimary) {
                                // keep primary as-is
                            } else {
                                val fallback = repoPlacementService.selectPrimaryNode(setOf(primaryNodeId))
                                if (fallback != null && fallback.nodeId != localNodeId) {
                                    val okFallback = gitStorageRemoteClient.initRepository(fallback.host, initRequest)
                                    if (okFallback) {
                                        updatePrimary(location, primaryNodeId, fallback.nodeId)
                                    } else {
                                        log.error { "[RepoCreate] remote init failed. fallback local repoId=${repository.id}" }
                                        updatePrimary(location, primaryNodeId, localNodeId)
                                        repositoryInitOrchestrator.initializeAsync(repository, defaultBranchFullRef, command)
                                    }
                                } else {
                                    log.error { "[RepoCreate] primary init failed and no remote fallback. local init repoId=${repository.id}" }
                                    updatePrimary(location, primaryNodeId, localNodeId)
                                    repositoryInitOrchestrator.initializeAsync(repository, defaultBranchFullRef, command)
                                }
                            }
                        }

                        LogContext.with(
                            "eventType" to "REPO_CREATED",
                            "repoId" to repository.id.toString()
                        ) {
                            events.publishEvent(RepositoryCreated(repository.id, repository.owner.id, repository.name))
                        }
                        log.info { "저장소 생성 이벤트 발행 완료: repositoryId=${repository.id}" }
                    } catch (e: Exception) {
                        log.error(e) { "저장소 생성 이벤트 발행 실패: repositoryId=${repository.id}" }
                    }
                }
            })
        }
        registerRepoCacheEvictionAfterCommitForRepo(
            evictor,
            repository.id,
            evictDetailAndBranches = true,
            evictLists = true
        )
        return repository
    }

    private fun updatePrimary(location: com.example.gitserver.module.gitindex.storage.domain.RepoLocationEntity?, oldPrimary: String, newPrimary: String) {
        val loc = location ?: return
        if (loc.primaryNodeId == newPrimary) return
        repoReplicaRepository.deleteByIdRepoIdAndIdNodeId(loc.repoId, newPrimary)
        if (repoReplicaRepository.findByIdRepoIdAndIdNodeId(loc.repoId, oldPrimary) == null) {
            repoReplicaRepository.save(
                RepoReplicaEntity(
                    id = RepoReplicaId(repoId = loc.repoId, nodeId = oldPrimary),
                    health = "unknown",
                    lagMs = null,
                    lagCommits = null
                )
            )
        }
        loc.primaryNodeId = newPrimary
        repoLocationRepository.save(loc)
    }
}
