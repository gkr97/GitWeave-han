package com.example.gitserver.module.repository.application.command.handler

import com.example.gitserver.module.common.application.service.CommonCodeCacheService
import com.example.gitserver.common.cache.RepoCacheEvictor
import com.example.gitserver.common.cache.registerRepoCacheEvictionAfterCommit
import com.example.gitserver.common.util.LogContext
import com.example.gitserver.module.repository.domain.Collaborator
import com.example.gitserver.module.repository.domain.event.*
import com.example.gitserver.module.repository.domain.policy.RepoAccessPolicy
import com.example.gitserver.module.repository.exception.*
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.user.exception.UserNotFoundException
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import mu.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CollaboratorCommandHandler(
    private val collaboratorRepository: CollaboratorRepository,
    private val repositoryRepository: RepositoryRepository,
    private val userRepository: UserRepository,
    private val commonCodeCacheService: CommonCodeCacheService,
    private val evictor: RepoCacheEvictor,
    private val access: RepoAccessPolicy,
    private val events: ApplicationEventPublisher
) {
    private val log = KotlinLogging.logger {}

    /**
     * 저장소에 협업자를 초대합니다.
     * 요청자가 저장소 소유자여야 하며, 이미 초대된 사용자는 중복 초대를 할 수 없습니다.
     *
     * @param repoId 저장소 ID
     * @param userIdToInvite 초대할 사용자 ID
     * @param requesterId 요청자 ID (저장소 소유자)
     */
    @Transactional
    fun inviteCollaborator(repoId: Long, userIdToInvite: Long, requesterId: Long) {
        val repository = repositoryRepository.findById(repoId)
            .orElseThrow { RepositoryNotFoundException(repoId) }

        if (!access.isOwner(repoId, requesterId)) throw NotRepositoryOwnerException()

        val existing = collaboratorRepository.findByRepositoryIdAndUserId(repoId, userIdToInvite)
        if (existing != null) {
            if (existing.accepted) throw CollaboratorAlreadyAcceptedException()
            else throw CollaboratorAlreadyInvitedException()
        }

        val invitee = userRepository.findById(userIdToInvite)
            .orElseThrow { UserNotFoundException(repoId) }

        val roleCodeId = commonCodeCacheService.getCodeDetailsOrLoad("ROLE")
            .firstOrNull { it.code == "maintainer" }?.id
            ?: throw RoleCodeNotFoundException()

        collaboratorRepository.save(
            Collaborator(
                repository = repository,
                user = invitee,
                roleCodeId = roleCodeId,
                accepted = false
              )
        )

        LogContext.with(
            "eventType" to "COLLAB_INVITED",
            "repoId" to repoId.toString()
        ) {
            log.info { "[Collaborator] invited event published" }
            events.publishEvent(CollaboratorInvited(repoId, userIdToInvite))
        }
        registerRepoCacheEvictionAfterCommit(evictor, evictLists = true)
        log.info { "[Collaborator] invited: repoId=$repoId, userId=$userIdToInvite by $requesterId" }
    }


    /**
     * 협업자 초대를 수락합니다.
     *
     * @param repoId 저장소 ID
     * @param userId 협업자 사용자 ID
     */
    @Transactional
    fun acceptInvitation(repoId: Long, userId: Long) {
        val collab = collaboratorRepository.findByRepositoryIdAndUserId(repoId, userId)
            ?: throw CollaboratorNotFoundException(repoId, userId)
        if (collab.accepted) throw CollaboratorAlreadyAcceptedException()

        collab.accepted = true
        LogContext.with(
            "eventType" to "COLLAB_ACCEPTED",
            "repoId" to repoId.toString()
        ) {
            log.info { "[Collaborator] accepted event published" }
            events.publishEvent(CollaboratorAccepted(repoId, userId))
        }
        registerRepoCacheEvictionAfterCommit(evictor, evictLists = true)
    }

    /**
     * 협업자 초대를 거절합니다.
     *
     * @param repoId 저장소 ID
     * @param userId 협업자 사용자 ID
     */
    @Transactional
    fun rejectInvitation(repoId: Long, userId: Long) {
        val collab = collaboratorRepository.findByRepositoryIdAndUserId(repoId, userId)
            ?: throw CollaboratorNotFoundException(repoId, userId)
        collaboratorRepository.delete(collab)

        LogContext.with(
            "eventType" to "COLLAB_REJECTED",
            "repoId" to repoId.toString()
        ) {
            log.info { "[Collaborator] rejected event published" }
            events.publishEvent(CollaboratorRejected(repoId, userId))
        }
        registerRepoCacheEvictionAfterCommit(evictor, evictLists = true)
    }

    /**
     * 협업자를 저장소에서 제거합니다.
     * 요청자가 저장소 소유자여야 합니다.
     *
     * @param repoId 저장소 ID
     * @param userId 제거할 협업자 사용자 ID
     * @param requesterId 요청자 ID (저장소 소유자)
     */
    @Transactional
    fun removeCollaborator(repoId: Long, userId: Long, requesterId: Long) {
        if (!access.isOwner(repoId, requesterId)) throw NotRepositoryOwnerException()
        if (!collaboratorRepository.existsByRepositoryIdAndUserId(repoId, userId))
            throw CollaboratorNotFoundException(repoId, userId)

        collaboratorRepository.deleteByRepositoryIdAndUserId(repoId, userId)

        LogContext.with(
            "eventType" to "COLLAB_REMOVED",
            "repoId" to repoId.toString()
        ) {
            log.info { "[Collaborator] removed event published" }
            events.publishEvent(CollaboratorRemoved(repoId, userId))
        }
        registerRepoCacheEvictionAfterCommit(evictor, evictLists = true)
    }
}
