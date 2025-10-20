package com.example.gitserver.module.repository.application.query

import com.example.gitserver.module.common.application.service.CommonCodeCacheService
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import com.example.gitserver.module.repository.interfaces.dto.CollaboratorResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CollaboratorQueryService(
    private val collaboratorRepository: CollaboratorRepository,
    private val commonCodeCacheService: CommonCodeCacheService,
) {

    private val log = mu.KotlinLogging.logger {}
    /**
     * 저장소의 협업자 목록을 조회합니다.
     * 요청자는 해당 저장소에 대한 접근 권한이 있어야 합니다.
     *
     * @param repoId 저장소 ID
     * @param requesterId 요청자 ID
     * @return 협업자 목록
     */
    @Transactional(readOnly = true)
    fun getCollaborators(repoId: Long, requesterId: Long): List<CollaboratorResponse> {

        log.info { "저장소 ID $repoId 에 대한 협업자 목록 조회 요청 by 사용자 $requesterId" }
        val isOwner = collaboratorRepository.findOwnerIdByRepositoryId(repoId)?.let { it == requesterId } ?: false
        val isCollaborator = collaboratorRepository.existsByRepositoryIdAndUserId(repoId, requesterId)
        if (!isOwner && !isCollaborator) {
            throw IllegalAccessException("해당 저장소에 대한 접근 권한이 없습니다")
        }

        val collaborators = collaboratorRepository.findAllWithUserByRepositoryId(repoId)

        val roleCodeMap = commonCodeCacheService.getCodeDetailsOrLoad("ROLE")
            .associateBy { it.id }

        log.info { "저장소 ID $repoId 대한 협업자 조회 완료: ${collaborators.size}명" }
        return collaborators.map {
            val roleCodeName = roleCodeMap[it.roleCodeId]?.code ?: "unknown"
            CollaboratorResponse(
                userId = it.user.id,
                name = it.user.name,
                email = it.user.email,
                roleCode = roleCodeName,
                accepted = it.accepted,
                invitedAt = it.invitedAt
            )
        }
    }
}
