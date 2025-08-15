package com.example.gitserver.module.repository.application.command.handler

import com.example.gitserver.module.common.service.CommonCodeCacheService
import com.example.gitserver.module.repository.domain.Collaborator
import com.example.gitserver.module.repository.exception.*
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.user.exception.UserNotFoundException
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CollaboratorCommandHandler(
    private val collaboratorRepository: CollaboratorRepository,
    private val repositoryRepository: RepositoryRepository,
    private val userRepository: UserRepository,
    private val commonCodeCacheService: CommonCodeCacheService,
) {
    private val log = KotlinLogging.logger {}

    @Transactional
    fun inviteCollaborator(repoId: Long, userIdToInvite: Long, requesterId: Long) {
        val repository = repositoryRepository.findById(repoId)
            .orElseThrow { RepositoryNotFoundException(repoId) }

        if (repository.owner.id != requesterId) {
            throw NotRepositoryOwnerException()
        }

        if (collaboratorRepository.existsByRepositoryIdAndUserId(repoId, userIdToInvite)) {
            throw CollaboratorAlreadyExistsException()
        }

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

        val newCollaborator = Collaborator(
            repository = repository,
            user = invitee,
            roleCodeId = roleCodeId,
            accepted = false
        )

        collaboratorRepository.save(newCollaborator)
        log.info { "[Collaborator] 초대 요청: repoId=$repoId, userId=$userIdToInvite by $requesterId" }
    }

    @Transactional
    fun acceptInvitation(repoId: Long, userId: Long) {
        val collaborator = collaboratorRepository.findByRepositoryIdAndUserId(repoId, userId)
            ?: throw CollaboratorNotFoundException(repoId, userId)

        if (collaborator.accepted) { throw CollaboratorAlreadyAcceptedException() }

        collaborator.accepted = true
        log.info { "[Collaborator] 초대 수락 완료 - repoId=$repoId, userId=$userId" }
    }

    @Transactional
    fun rejectInvitation(repoId: Long, userId: Long) {
        val collaborator = collaboratorRepository.findByRepositoryIdAndUserId(repoId, userId)
            ?: throw CollaboratorNotFoundException(repoId, userId)

        collaboratorRepository.delete(collaborator)
        log.info { "[Collaborator] 초대 거절 및 삭제 - repoId=$repoId, userId=$userId" }
    }

    @Transactional
    fun removeCollaborator(repoId: Long, userId: Long, requesterId: Long) {
        val repository = repositoryRepository.findById(repoId)
            .orElseThrow { RepositoryNotFoundException(repoId) }

        if (repository.owner.id != requesterId) { throw NotRepositoryOwnerException() }

        if (!collaboratorRepository.existsByRepositoryIdAndUserId(repoId, userId)) {
            throw CollaboratorNotFoundException(repoId, userId)
        }

        collaboratorRepository.deleteByRepositoryIdAndUserId(repoId, userId)
        log.info { "[Collaborator] collaborator 강제 삭제 - repoId=$repoId, userId=$userId, by=$requesterId" }
    }
}
