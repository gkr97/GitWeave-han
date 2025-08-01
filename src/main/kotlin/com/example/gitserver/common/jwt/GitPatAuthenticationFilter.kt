package com.example.gitserver.common.jwt

import com.example.gitserver.module.common.service.CommonCodeCacheService
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import com.example.gitserver.module.user.infrastructure.persistence.PersonalAccessTokenRepository
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import mu.KLogging
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest
import java.util.Base64

@Component
class GitPatAuthenticationFilter(
    private val userRepository: UserRepository,
    private val patRepository: PersonalAccessTokenRepository,
    private val repositoryRepository: RepositoryRepository,
    private val collaboratorRepository: CollaboratorRepository,
    private val commonCodeCacheService: CommonCodeCacheService
) : OncePerRequestFilter() {

    private val log = KLogging().logger

    /**
     * Git 저장소 접근을 위한 Personal Access Token(PAT) 인증 필터
     * 요청 URI가 git 저장소 경로인 경우 PAT 인증을 수행합니다.
     */
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val uri = request.requestURI
        log.debug("요청 URI: {}", uri)

        if (!uri.matches(Regex("^/.+/.+\\.git(/.*)?$"))) {
            log.trace("git repository 경로가 아니므로 인증 필터를 통과시킴: $uri")
            chain.doFilter(request, response)
            return
        }

        val authHeader = request.getHeader("Authorization")
        var userId: Long? = null

        if (authHeader != null && authHeader.startsWith("Basic ")) {
            val base64Credentials = authHeader.substringAfter("Basic ").trim()
            try {
                val credentials = String(Base64.getDecoder().decode(base64Credentials))
                val parts = credentials.split(":", limit = 2)
                log.debug("디코딩된 credentials: {}", credentials)

                if (parts.size == 2) {
                    val username = parts[0]
                    val pat = parts[1]
                    log.info("인증 시도 - username(email): {}", username)

                    val user = userRepository.findByEmailAndIsDeletedFalse(username)
                    if (user != null) {
                        val patHash = hashPat(pat)
                        val validPat = patRepository.findByUserIdAndTokenHashAndIsActiveTrue(user.id, patHash)
                        if (validPat != null && (validPat.expiresAt == null || validPat.expiresAt!!.isAfter(java.time.LocalDateTime.now()))) {
                            userId = user.id
                            log.info("PAT 인증 성공 - userId: {}", user.id)
                        } else {
                            log.warn("유효하지 않은 PAT 또는 만료됨 - userId: {}", user.id)
                        }
                    } else {
                        log.warn("존재하지 않는 사용자(email): {}", username)
                    }
                } else {
                    log.warn("Authorization 헤더 포맷 오류: 콜론(:) 기준 2개 값이 아님")
                }
            } catch (e: Exception) {
                log.error("Authorization 헤더 파싱/디코딩 중 오류 발생: ${e.message}", e)
                response.sendError(400, "Invalid Authorization header")
                return
            }
        } else {
            log.info("Authorization 헤더가 없거나 Basic 스킴이 아님")
        }

        val matcher = Regex("/([^/]+)/([\\w.-]+)\\.git.*").find(uri)
        val ownerName = matcher?.groups?.get(1)?.value
        val repoName = matcher?.groups?.get(2)?.value

        val owner = ownerName?.let { userRepository.findByNameAndIsDeletedFalse(it) }
        val ownerId = owner?.id

        log.debug("파싱된 ownerId: {}, repoName: {}", ownerId, repoName)

        val repo = if (ownerId != null && repoName != null) {
            repositoryRepository.findByOwnerIdAndNameAndIsDeletedFalse(ownerId, repoName)
        } else null

        if (repo == null) {
            log.warn("Repository not found - ownerId: {}, repoName: {}", ownerId, repoName)
            response.sendError(404, "Repository not found")
            return
        }

        val publicCodeId = commonCodeCacheService.getCodeDetailsOrLoad("VISIBILITY").firstOrNull()?.id
        if (repo.visibilityCodeId == publicCodeId) {
            log.info("공개 저장소 접근 허용 - repoId: {}", repo.id)
            chain.doFilter(request, response)
            return
        }

        if (userId == null) {
            log.warn("인증 실패 - 인증 정보 없음")
            response.setHeader("WWW-Authenticate", "Basic realm=\"Git\"")
            response.sendError(401, "Unauthorized")
            return
        }

        if (repo.owner.id == userId || collaboratorRepository.existsByRepositoryIdAndUserIdAndAcceptedTrue(repo.id, userId)) {
            log.info("접근 허용 - userId: {}, repoId: {}", userId, repo.id)
            chain.doFilter(request, response)
            return
        }

        log.warn("접근 거부(Forbidden) - userId: {}, repoId: {}", userId, repo.id)
        throw BadCredentialsException("Unauthorized")
    }

    fun hashPat(pat: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(pat.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
}
