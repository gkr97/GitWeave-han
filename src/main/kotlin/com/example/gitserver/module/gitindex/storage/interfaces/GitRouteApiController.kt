package com.example.gitserver.module.gitindex.storage.interfaces

import com.example.gitserver.module.gitindex.storage.application.GitRoutingAppService
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.context.annotation.Profile

@RestController
@Profile("gitstorage")
class GitRouteApiController(
    private val gitRoutingService: GitRoutingAppService,
    private val userRepository: UserRepository,
    private val repositoryRepository: RepositoryRepository,
    private val trafficTracker: com.example.gitserver.module.gitindex.storage.infrastructure.routing.GitRepoTrafficTracker,
    @Value("\${git.routing.api-key:}") private val apiKey: String
) {

    @GetMapping("/internal/git/route")
    fun route(
        @RequestParam owner: String,
        @RequestParam repo: String,
        @RequestParam(defaultValue = "read") mode: String,
        @RequestHeader(value = "X-Git-Route-Key", required = false) providedKey: String?
    ): ResponseEntity<GitRouteResponse> {
        if (apiKey.isNotBlank() && apiKey != providedKey) {
            return ResponseEntity.status(401).build()
        }

        val userId = userRepository.findByNameAndIsDeletedFalse(owner)?.id
            ?: return ResponseEntity.notFound().build()

        val repoEntity = repositoryRepository.findByOwnerIdAndNameAndIsDeletedFalse(userId, repo)
            ?: return ResponseEntity.notFound().build()

        val decision = if (mode.lowercase() == "write") {
            gitRoutingService.routeForWrite(repoEntity.id)
        } else {
            gitRoutingService.routeForRead(repoEntity.id)
        } ?: return ResponseEntity.status(503).build()

        trafficTracker.record(repoEntity.id)
        return ResponseEntity.ok(
            GitRouteResponse(
                repoId = repoEntity.id,
                nodeId = decision.nodeId,
                host = decision.host,
                role = decision.role.name
            )
        )
    }
}

data class GitRouteResponse(
    val repoId: Long,
    val nodeId: String,
    val host: String,
    val role: String
)
