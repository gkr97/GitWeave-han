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

    /**
     * 저장소에 협업자를 초대합니다.
     * 요청자가 저장소 소유자여야 하며, 이미 초대된 사용자는 중복 초대를 할 수 없습니다.
     *
     * @param repoId 저장소 ID
     * @param userIdToInvite 초대할 사용자 ID
     * @param requesterId 요청자 ID (저장소 소유자)
     * @throws RepositoryNotFoundException 저장소가 존재하지 않는 경우
     * @throws NotRepositoryOwnerException 요청자가 저장소 소유자가 아닌 경우
     * @throws CollaboratorAlreadyExistsException 이미 협업자로 등록된 경우
     * @throws CollaboratorAlreadyAcceptedException 이미 수락된 협업자인 경우
     * @throws CollaboratorAlreadyInvitedException 이미 초대된 협업자인 경우
     * @throws UserNotFoundException 초대할 사용자가 존재하지 않는 경우
     * @throws RoleCodeNotFoundException 역할 코드가 존재하지 않는 경우
     */
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

    /**
     * 협업자 초대를 수락합니다.
     * 초대된 사용자가 해당 저장소에 대한 협업 요청을 수락합니다.
     *
     * @param repoId 저장소 ID
     * @param userId 초대된 사용자 ID
     * @throws CollaboratorNotFoundException 협업자가 존재하지 않는 경우
     * @throws CollaboratorAlreadyAcceptedException 이미 수락된 협업자인 경우
     */
    @Transactional
    fun acceptInvitation(repoId: Long, userId: Long) {
        val collaborator = collaboratorRepository.findByRepositoryIdAndUserId(repoId, userId)
            ?: throw CollaboratorNotFoundException(repoId, userId)

        if (collaborator.accepted) { throw CollaboratorAlreadyAcceptedException() }

        collaborator.accepted = true
        log.info { "[Collaborator] 초대 수락 완료 - repoId=$repoId, userId=$userId" }
    }

    /**
     * 협업자 초대를 거절합니다.
     * 초대된 사용자가 해당 저장소에 대한 협업 요청을 거절하고, 협업자 목록에서 삭제합니다.
     *
     * @param repoId 저장소 ID
     * @param userId 초대된 사용자 ID
     * @throws CollaboratorNotFoundException 협업자가 존재하지 않는 경우
     */
    @Transactional
    fun rejectInvitation(repoId: Long, userId: Long) {
        val collaborator = collaboratorRepository.findByRepositoryIdAndUserId(repoId, userId)
            ?: throw CollaboratorNotFoundException(repoId, userId)

        collaboratorRepository.delete(collaborator)
        log.info { "[Collaborator] 초대 거절 및 삭제 - repoId=$repoId, userId=$userId" }
    }

    /**
     * 협업자를 강제 삭제합니다.
     * 요청자가 저장소 소유자여야 하며, 해당 협업자가 존재해야 합니다.
     *
     * @param repoId 저장소 ID
     * @param userId 협업자 사용자 ID
     * @param requesterId 요청자 ID (저장소 소유자)
     * @throws RepositoryNotFoundException 저장소가 존재하지 않는 경우
     * @throws NotRepositoryOwnerException 요청자가 저장소 소유자가 아닌 경우
     * @throws CollaboratorNotFoundException 협업자가 존재하지 않는 경우
     */
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
