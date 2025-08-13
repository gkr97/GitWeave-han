package com.example.gitserver.module.repository.application.query

import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.user.application.query.JwtQueryService
import com.example.gitserver.module.user.application.service.PatService
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RepositoryAccessQueryService(
    private val repoRepository: RepositoryRepository,
    private val patService: PatService,
    private val jwtQueryService: JwtQueryService,
    private val repositoryAccessService: RepositoryAccessService,
) {

    private val log = KotlinLogging.logger {}

    /**
     * 저장소 접근 권한을 확인합니다.
     * - 공개 저장소: 인증 없이 접근 허용
     * - 비공개 저장소: PAT 또는 JWT 인증 필요
     * @param repoName 저장소 이름
     * @param ownerId 소유자 ID
     * @param authorization Authorization 헤더 (PAT 또는 JWT)
     * @return AccessResult. Authorized(userId) 또는 NotFound, Unauthorized, Forbidden 중 하나
     */
    @Transactional(readOnly = true)
    fun checkAccess(
        repoName: String,
        ownerId: Long,
        authorization: String?
    ): AccessResult {
        log.info { "Check access: repoName=$repoName, ownerId=$ownerId, hasAuthHeader=${authorization != null}" }

        val repo = repoRepository.findByOwnerIdAndNameAndIsDeletedFalse(ownerId, repoName)
            ?: run {
                log.warn { "Repository not found: ownerId=$ownerId, repoName=$repoName" }
                return AccessResult.NotFound
            }

        // 1) 공개 저장소면 통과
        if (repositoryAccessService.isPublicRepository(repo)) {
            log.info { "Public repo -> allow anonymously: ownerId=$ownerId, repoName=$repoName" }
            return AccessResult.Authorized(null)
        }

        // 2) 인증 헤더로 해석 하기
        val userId = resolveUserId(authorization)
            ?: run {
                log.warn { "Unauthorized: cannot resolve userId (hasAuthHeader=${authorization != null})" }
                return AccessResult.Unauthorized
            }

        // 3) 권한 판단 위임
        val allowed = repositoryAccessService.hasReadAccess(repo, userId, requireAccepted = true)
        if (allowed) {
            log.info { "Access granted: userId=$userId, repoId=${repo.id}" }
            return AccessResult.Authorized(userId)
        }

        log.warn { "Forbidden: userId=$userId, repoId=${repo.id}" }
        return AccessResult.Forbidden
    }

    /**
     * Authorization 헤더에서 userId를 해석합니다.
     * - PAT(Basic/PAT) 또는 JWT(Bearer) 방식 지원
     * - PAT 우선 처리
     * @return userId 또는 null (인증 실패)
     */
    private fun resolveUserId(authorization: String?): Long? {
        val fromPat = patService.resolveUserIdByAuthHeader(authorization)
        if (fromPat != null) {
            log.debug { "[Auth] resolved by PAT: userId=$fromPat" }
            return fromPat
        }
        val fromJwt = jwtQueryService.resolveUserIdByBearer(authorization)
        if (fromJwt != null) {
            log.debug { "[Auth] resolved by JWT: userId=$fromJwt" }
            return fromJwt
        }
        return null
    }

    sealed class AccessResult {
        data class Authorized(val userId: Long?) : AccessResult()
        data object NotFound : AccessResult()
        data object Unauthorized : AccessResult()
        data object Forbidden : AccessResult()
    }
}
