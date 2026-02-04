package com.example.gitserver.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class InternalGitApiKeyFilter(
    @Value("\${git.routing.api-key:}") private val routeKey: String,
    @Value("\${git.routing.admin-key:}") private val adminKey: String
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val path = request.requestURI
        if (path.startsWith("/internal/git/route")) {
            if (routeKey.isNotBlank()) {
                val provided = request.getHeader("X-Git-Route-Key")
                if (provided != routeKey) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
                    return
                }
            }
        } else if (path.startsWith("/internal/git/routing") || path.startsWith("/internal/git/storage")) {
            if (adminKey.isNotBlank()) {
                val provided = request.getHeader("X-Git-Admin-Key")
                if (provided != adminKey) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
                    return
                }
            }
        }

        filterChain.doFilter(request, response)
    }
}
