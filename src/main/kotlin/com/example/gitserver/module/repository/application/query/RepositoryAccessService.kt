package com.example.gitserver.module.repository.application.query

import com.example.gitserver.module.common.service.CommonCodeCacheService
import com.example.gitserver.module.repository.domain.Repository
import com.example.gitserver.module.repository.exception.RepositoryAccessDeniedException
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import mu.KotlinLogging
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service

@Service
class RepositoryAccessService(
    private val commonCodeCacheService: CommonCodeCacheService,
    private val collaboratorRepository: CollaboratorRepository
) {
    private val log = KotlinLogging.logger {}

    /**
     * 읽기 권한 체크(실패 시 예외)
     * @param requireAccepted true: collaborator 가 초대 수락(accepted=true)이어야 접근 허용(기본)
     */
    fun checkReadAccessOrThrow(repo: Repository, currentUserId: Long?, requireAccepted: Boolean = true) {
        log.debug { "[Access] read-check start repoId=${repo.id} userId=${currentUserId ?: "anon"}" }

        if (isPublicRepository(repo)) {
            log.debug { "[Access] public repository -> allow repoId=${repo.id}" }
            return
        }

        val owner = isOwner(repo, currentUserId)
        if (owner) {
            log.debug { "[Access] owner -> allow repoId=${repo.id} userId=$currentUserId" }
            return
        }

        val collaborator = isCollaborator(repo, currentUserId,  requireAccepted)
        if (collaborator) {
            log.debug {
                "[Access] collaborator(requireAccepted=$requireAccepted) -> allow repoId=${repo.id} userId=$currentUserId"
            }
            return
        }

        log.warn {
            "[Access] forbidden repoId=${repo.id} userId=${currentUserId ?: "anon"} " +
                    "public=false owner=false collaborator=false"
        }
        throw RepositoryAccessDeniedException(repo.id, currentUserId)
    }

    /**
     * 읽기 권한 체크(성공 시 true, 실패 시 false)
     * @param requireAccepted true: collaborator 가 초대 수락(accepted=true)이어야 접근 허용(기본)
     */
    fun hasReadAccess(repo: Repository, currentUserId: Long?, requireAccepted: Boolean = true): Boolean {
        if (isPublicRepository(repo)) return true
        if (isOwner(repo, currentUserId)) return true
        if (isCollaborator(repo, currentUserId, requireAccepted)) return true
        return false
    }

    fun isPublicRepository(repo: Repository): Boolean {
        val publicCodeId = commonCodeCacheService
            .getCodeDetailsOrLoad("VISIBILITY")
            .firstOrNull { it.code.equals("PUBLIC", true) }
            ?.id
        val result = (repo.visibilityCodeId == publicCodeId)
        log.debug { "[Access] isPublic=$result repoId=${repo.id} publicCodeId=$publicCodeId repo.visibility=${repo.visibilityCodeId}" }
        return result
    }

    private fun isOwner(repo: Repository, currentUserId: Long?): Boolean {
        val result = (currentUserId != null && repo.owner.id == currentUserId)
        log.debug { "[Access] isOwner=$result repoOwnerId=${repo.owner.id} userId=${currentUserId ?: "null"}" }
        return result
    }

    private fun isCollaborator(repo: Repository, currentUserId: Long?, requireAccepted: Boolean): Boolean {
        if (currentUserId == null) {
            log.debug { "[Access] isCollaborator=  false (user null)" }
            return false
        }
        val result = if (requireAccepted) {
            collaboratorRepository.existsByRepositoryIdAndUserIdAndAcceptedTrue(repo.id, currentUserId)
        } else {
            collaboratorRepository.existsByRepositoryIdAndUserId(repo.id, currentUserId)
        }
        log.debug {
            "[Access] isCollaborator= $result repoId=${repo.id} userId=$currentUserId requireAccepted=$requireAccepted"
        }
        return result
    }
}
