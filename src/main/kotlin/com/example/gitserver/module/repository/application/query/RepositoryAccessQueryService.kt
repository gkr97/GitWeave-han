package com.example.gitserver.module.repository.application.query

import com.example.gitserver.module.common.service.CommonCodeCacheService
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.user.application.query.JwtQueryService
import com.example.gitserver.module.user.application.service.PatService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RepositoryAccessQueryService(
    private val repoRepository: RepositoryRepository,
    private val collaboratorRepository: CollaboratorRepository,
    private val patService: PatService,
    private val jwtQueryService: JwtQueryService,
    private val commonCodeCacheService: CommonCodeCacheService,
) {

    private val log = mu.KotlinLogging.logger { }

    /**
     * 인증 및 권한 체크 로직
     * 이 메서드는 저장소 접근 권한을 확인합니다.
     * - 저장소가 존재하지 않으면 NotFound 반환
     * - 저장소가 공개(public)인 경우 Authorized 반환
     * - 인증 헤더가 없거나 유효하지 않은 경우 Unauthorized 반환
     */
    @Transactional(readOnly = true)
    fun checkAccess(
        repoName: String,
        ownerId: Long,
        authorization: String?
    ): AccessResult {
        log.info("Check access: repoName={}, ownerId={}, authHeader={}", repoName, ownerId, authorization != null)

        val repo = repoRepository.findByOwnerIdAndNameAndIsDeletedFalse(ownerId, repoName)
            ?: run {
                log.warn("Repository not found: ownerId={}, repoName={}", ownerId, repoName)
                return AccessResult.NotFound
            }

        val visibilityCode = commonCodeCacheService.getCodeDetailsOrLoad("VISIBILITY")
            .firstOrNull { it.code == "public" }?.id
            ?: run {
                log.error("Visibility code for 'public' not found")
                return AccessResult.NotFound
            }

        if (repo.visibilityCodeId == visibilityCode) {
            log.info("Public repo. Access granted: ownerId={}, repoName={}", ownerId, repoName)
            return AccessResult.Authorized(null)
        }

        val userId = patService.resolveUserIdByAuthHeader(authorization)
            ?: jwtQueryService.resolveUserIdByBearer(authorization)
            ?: run {
                log.warn("Unauthorized: No valid userId found (authHeader={})", authorization != null)
                return AccessResult.Unauthorized
            }

        if (repo.owner.id == userId) {
            log.info("Repo owner access granted. ownerId={}, repoName={}", ownerId, repoName)
            return AccessResult.Authorized(userId)
        }
        if (collaboratorRepository.existsByRepositoryIdAndUserIdAndAcceptedTrue(repo.id, userId)) {
            log.info("Collaborator access granted. userId={}, repoId={}", userId, repo.id)
            return AccessResult.Authorized(userId)
        }
        log.warn("Forbidden: userId={} for repoId={}", userId, repo.id)
        return AccessResult.Forbidden
    }

    sealed class AccessResult {
        data class Authorized(val userId: Long?) : AccessResult()
        data object NotFound : AccessResult()
        data object Unauthorized : AccessResult()
        data object Forbidden : AccessResult()
    }
}
