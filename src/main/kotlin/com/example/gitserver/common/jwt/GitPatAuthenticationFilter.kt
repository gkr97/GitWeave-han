package com.example.gitserver.common.jwt

import com.example.gitserver.module.repository.application.query.RepositoryAccessQueryService
import com.example.gitserver.module.repository.application.query.RepositoryAccessQueryService.AccessResult
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import mu.KLogging
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class GitPatAuthenticationFilter(
    private val userRepository: UserRepository,
    private val userDetailsService: UserDetailsService,
    private val accessQueryService: RepositoryAccessQueryService,
) : OncePerRequestFilter() {

    private val log = KLogging().logger

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val uri = request.requestURI
        log.debug("요청 URI: {}", uri)

        if (!uri.matches(Regex("^/.+/.+\\.git(/.*)?$"))) {
            log.trace("git repository 경로가 아니므로 인증 필터를 통과시킴: {}", uri)
            chain.doFilter(request, response)
            return
        }

        val matcher = Regex("/([^/]+)/([\\w.-]+)\\.git.*").find(uri)
        val ownerName = matcher?.groups?.get(1)?.value
        val repoName = matcher?.groups?.get(2)?.value

        if (ownerName.isNullOrBlank() || repoName.isNullOrBlank()) {
            log.warn("URI 파싱 실패 - ownerName 또는 repoName 누락: {}", uri)
            response.sendError(400, "Bad repository path")
            return
        }

        val owner = userRepository.findByNameAndIsDeletedFalse(ownerName)
        val ownerId = owner?.id
        if (ownerId == null) {
            log.warn("Owner not found - ownerName: {}", ownerName)
            response.sendError(404, "Repository not found")
            return
        }

        val authHeader = request.getHeader("Authorization")
        log.debug("Authorization 헤더 존재 여부: {}", authHeader != null)

        val result = accessQueryService.checkAccess(
            repoName = repoName,
            ownerId = ownerId,
            authorization = authHeader
        )

        when (result) {
            is AccessResult.NotFound -> {
                log.warn("Repository not found - ownerId: {}, repoName: {}", ownerId, repoName)
                response.sendError(404, "Repository not found")
                return
            }
            is AccessResult.Unauthorized -> {
                log.warn("Unauthorized - 인증 정보 없음/유효하지 않음: ownerId={}, repoName={}", ownerId, repoName)
                response.setHeader("WWW-Authenticate", "Basic realm=\"Git\"")
                response.sendError(401, "Unauthorized")
                return
            }
            is AccessResult.Forbidden -> {
                log.warn("접근 거부(Forbidden) - ownerId={}, repoName={}", ownerId, repoName)
                response.sendError(403, "Forbidden")
                return
            }
            is AccessResult.Authorized -> {
                val userId = result.userId
                if (userId != null) {
                    val user = userRepository.findByIdAndIsDeletedFalse(userId)
                        ?: throw BadCredentialsException("User not found")
                    val userDetails = userDetailsService.loadUserByUsername(user.email)
                    val authentication = UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.authorities
                    )
                    SecurityContextHolder.getContext().authentication = authentication
                    log.info("접근 허용(Authenticated) - userId: {}, repo: {}/{}", userId, ownerName, repoName)
                } else {
                    log.info("접근 허용(Anonymous Public) - repo: {}/{}", ownerName, repoName)
                }

                chain.doFilter(request, response)
                return
            }
        }
    }
}
