package com.example.gitserver.module.repository.application.command.handler

import com.example.gitserver.common.util.GitRefUtils.toFullRef
import com.example.gitserver.module.common.service.CommonCodeCacheService
import com.example.gitserver.module.gitindex.application.query.CommitQueryService
import com.example.gitserver.module.gitindex.domain.event.GitEvent
import com.example.gitserver.module.gitindex.domain.port.GitRepositoryPort
import com.example.gitserver.module.repository.application.command.CreateRepositoryCommand
import com.example.gitserver.module.repository.domain.Branch
import com.example.gitserver.module.repository.domain.Collaborator
import com.example.gitserver.module.repository.domain.Repository
import com.example.gitserver.module.repository.domain.event.GitEventPublisher
import com.example.gitserver.module.repository.exception.*
import com.example.gitserver.module.repository.infrastructure.persistence.BranchRepository
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.ZoneOffset

@Service
class CreateRepositoryCommandHandler(
    private val repositoryRepository: RepositoryRepository,
    private val gitRepositoryPort: GitRepositoryPort,
    private val branchRepository: BranchRepository,
    private val gitEventPublisher: GitEventPublisher,
    private val commonCodeCacheService: CommonCodeCacheService,
    private val collaboratorRepository: CollaboratorRepository,
    private val userRepository: UserRepository,
    private val commitService: CommitQueryService,

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

        try {
            Thread.startVirtualThread {
                gitRepositoryPort.initEmptyGitDirectory(
                    repository,
                    initializeReadme = command.initializeReadme,
                    gitignoreTemplate = command.gitignoreTemplate,
                    licenseTemplate = command.licenseTemplate
                )
            }.join()
            log.info { "Git 저장소 초기화 완료: ${repository.name}" }
        } catch (ex: Exception) {
            log.error(ex) { "Git 저장소 초기화 실패: ${repository.name}, 롤백 수행" }
            repositoryRepository.delete(repository)
            gitRepositoryPort.deleteGitDirectories(repository)
            throw GitInitializationFailedException(repository.id)
        }

        val defaultBranchFullRef = toFullRef(command.defaultBranch)

        val branch = Branch.createDefault(repository, defaultBranchFullRef, command.owner)

        val headCommitHash = gitRepositoryPort.getHeadCommitHash(repository, defaultBranchFullRef)
        branch.updateHeadCommitHash(headCommitHash)

        val headCommit = commitService.getCommitInfo(repository.id, headCommitHash)
        branch.lastCommitAt = headCommit?.committedAt
            ?.atOffset(ZoneOffset.UTC)
            ?.toLocalDateTime()
            ?: branch.createdAt

        branchRepository.save(branch)
        log.info { "기본 브랜치 생성 완료: ${branch.name}, HEAD=$headCommitHash" }

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
                        gitEventPublisher.publish(
                            GitEvent(
                                eventType = "REPO_CREATED",
                                repositoryId = repository.id,
                                ownerId = repository.owner.id,
                                name = repository.name,
                                branch = branch.name
                            )
                        )
                        log.info { "저장소 생성 이벤트 발행 완료: repositoryId=${repository.id}" }
                    } catch (e: Exception) {
                        log.error(e) { "저장소 생성 이벤트 발행 실패: repositoryId=${repository.id}" }
                    }
                }
            })
        }

        return repository
    }
}
