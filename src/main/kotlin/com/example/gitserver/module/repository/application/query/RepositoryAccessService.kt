package com.example.gitserver.module.repository.application.query

import com.example.gitserver.module.common.application.service.CommonCodeCacheService
import com.example.gitserver.module.common.cache.RequestCache
import com.example.gitserver.module.repository.domain.Repository
import com.example.gitserver.module.repository.exception.RepositoryAccessDeniedException
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service

@Service
class RepositoryAccessService(
    private val commonCodeCacheService: CommonCodeCacheService,
    private val collaboratorRepository: CollaboratorRepository,
    private val requestCache: RequestCache
) {
    private val log = KotlinLogging.logger {}

    fun checkReadAccessOrThrow(repo: Repository, currentUserId: Long?, requireAccepted: Boolean = true) {
        log.debug { "[Access] read-check start repoId=${repo.id} userId=${currentUserId ?: "anon"}" }

        if (isPublicRepository(repo)) {
            log.debug { "[Access] public repository -> allow repoId=${repo.id}" }
            return
        }

        if (isOwner(repo, currentUserId)) {
            log.debug { "[Access] owner -> allow repoId=${repo.id} userId=$currentUserId" }
            return
        }

        if (isCollaborator(repo, currentUserId, requireAccepted)) {
            log.debug { "[Access] collaborator(requireAccepted=$requireAccepted) -> allow repoId=${repo.id} userId=$currentUserId" }
            return
        }

        log.warn {
            "[Access] forbidden repoId=${repo.id} userId=${currentUserId ?: "anon"} public=false owner=false collaborator=false"
        }
        throw RepositoryAccessDeniedException(repo.id, currentUserId)
    }

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
            log.debug { "[Access] isCollaborator=false (user null)" }
            return false
        }

        requestCache.getCollabExists(repo.id!!, currentUserId)?.let { cached ->
            log.debug { "[Access] isCollaborator (req-cache hit)=$cached repoId=${repo.id} userId=$currentUserId requireAccepted=$requireAccepted" }
            return cached
        }

        val exists = if (requireAccepted) {
            collaboratorRepository.existsByRepositoryIdAndUserIdAndAcceptedTrue(repo.id, currentUserId)
        } else {
            collaboratorRepository.existsByRepositoryIdAndUserId(repo.id, currentUserId)
        }

        requestCache.putCollabExists(repo.id, currentUserId, exists)
        log.debug { "[Access] isCollaborator (db)=$exists repoId=${repo.id} userId=$currentUserId requireAccepted=$requireAccepted" }
        return exists
    }
}
