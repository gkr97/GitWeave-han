package com.example.gitserver.common.jwt

import com.example.gitserver.module.user.infrastructure.security.CustomUserDetailsService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.annotation.Order
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * JwtAuthenticationFilter는 JWT 토큰을 검증하고, 유효한 경우 사용자 인증 정보를 SecurityContext에 설정
 * 이 필터는 요청이 들어올 때마다 실행됩니다.
 */
@Order(2)
@Component
class JwtAuthenticationFilter(
    private val jwtProvider: JwtProvider,
    private val userDetailsService: CustomUserDetailsService,
) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        try {
            val token = request.getHeader("Authorization")
                ?.takeIf { it.startsWith("Bearer ") }?.substring(7)
                ?: request.cookies?.firstOrNull { it.name == "ACCESS_TOKEN" }?.value

            if (!token.isNullOrBlank() && jwtProvider.validateToken(token)) {
                val userId = jwtProvider.getUserId(token)
                val userDetails: UserDetails = userDetailsService.loadUserById(userId)
                val auth = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
                auth.details = WebAuthenticationDetailsSource().buildDetails(request)
                SecurityContextHolder.getContext().authentication = auth
            }
        } catch (_: Exception) {
        }
        chain.doFilter(request, response)
    }

    /** 인증이 필요하지 않은 경로를 필터링합니다. */
    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val uri = request.requestURI
        return uri.startsWith("/api/v1/auth/") ||
                uri == "/error" ||
                uri.startsWith("/swagger-ui/") ||
                uri.startsWith("/v3/api-docs/") ||
                uri.startsWith("/h2-console/") ||
                uri.startsWith("/graphiql")
    }
}
