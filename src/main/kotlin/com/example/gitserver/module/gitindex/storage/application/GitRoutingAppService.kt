package com.example.gitserver.module.gitindex.storage.application

import com.example.gitserver.module.gitindex.storage.infrastructure.routing.GitRoutingService
import com.example.gitserver.module.gitindex.storage.domain.GitRouteDecision
import org.springframework.stereotype.Service
import org.springframework.context.annotation.Profile

@Service
@Profile("gitstorage")
class GitRoutingAppService(
    private val routingService: GitRoutingService
) {
    fun routeForRead(repoId: Long): GitRouteDecision? = routingService.routeForRead(repoId)

    fun routeForWrite(repoId: Long): GitRouteDecision? = routingService.routeForWrite(repoId)
}
