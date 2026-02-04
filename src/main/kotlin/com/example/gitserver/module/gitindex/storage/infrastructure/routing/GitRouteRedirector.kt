package com.example.gitserver.module.gitindex.storage.infrastructure.routing

import com.example.gitserver.module.gitindex.storage.application.GitRoutingAppService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.context.annotation.Profile

@Component
@Profile("gitstorage")
class GitRouteRedirector(
    private val gitRoutingService: GitRoutingAppService,
    @Value("\${git.routing.enabled:false}") private val routingEnabled: Boolean,
    @Value("\${git.routing.local-node-id:local}") private val localNodeId: String,
    @Value("\${git.routing.scheme:http}") private val routingScheme: String,
    @Value("\${git.routing.allowlist-repo-ids:}") private val allowlistRepoIdsRaw: String
) {
    private val log = KotlinLogging.logger {}
    private val allowlistRepoIds: Set<Long> = parseAllowlist(allowlistRepoIdsRaw)

    fun maybeRedirectRead(repoId: Long, request: HttpServletRequest, response: HttpServletResponse): Boolean {
        if (!routingEnabled) return false
        if (!isAllowed(repoId)) return false

        val route = gitRoutingService.routeForRead(repoId) ?: return false
        if (route.nodeId == localNodeId) return false

        return redirect(route.host, request, response)
    }

    fun maybeRedirectWrite(repoId: Long, request: HttpServletRequest, response: HttpServletResponse): Boolean {
        if (!routingEnabled) return false
        if (!isAllowed(repoId)) return false

        val route = gitRoutingService.routeForWrite(repoId) ?: return false
        if (route.nodeId == localNodeId) return false

        return redirect(route.host, request, response)
    }

    private fun redirect(host: String, request: HttpServletRequest, response: HttpServletResponse): Boolean {
        val query = request.queryString?.let { "?$it" } ?: ""
        val target = "$routingScheme://$host${request.requestURI}$query"
        log.info { "[GitRoute] redirect ${request.method} ${request.requestURI} -> $target" }
        response.status = 307
        response.setHeader("Location", target)
        return true
    }

    private fun isAllowed(repoId: Long): Boolean =
        allowlistRepoIds.isEmpty() || allowlistRepoIds.contains(repoId)

    private fun parseAllowlist(raw: String): Set<Long> =
        raw.split(",")
            .mapNotNull { it.trim().takeIf { t -> t.isNotEmpty() }?.toLongOrNull() }
            .toSet()
}
