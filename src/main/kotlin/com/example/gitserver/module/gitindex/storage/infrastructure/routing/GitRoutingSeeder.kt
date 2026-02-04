package com.example.gitserver.module.gitindex.storage.infrastructure.routing

import com.example.gitserver.module.gitindex.storage.domain.GitNodeEntity
import com.example.gitserver.module.gitindex.storage.domain.RepoLocationEntity
import com.example.gitserver.module.gitindex.storage.infrastructure.persistence.GitNodeRepository
import com.example.gitserver.module.gitindex.storage.infrastructure.persistence.RepoLocationRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.boot.context.event.ApplicationReadyEvent
import java.time.LocalDateTime
import org.springframework.context.annotation.Profile

@Component
@Profile("gitstorage")
class GitRoutingSeeder(
    private val gitNodeRepository: GitNodeRepository,
    private val repoLocationRepository: RepoLocationRepository,
    private val repositoryRepository: RepositoryRepository,
    @Value("\${git.routing.seed-local:false}") private val seedEnabled: Boolean,
    @Value("\${git.routing.local-node-id:local}") private val localNodeId: String,
    @Value("\${git.routing.local-host:localhost:8080}") private val localHost: String,
    @Value("\${git.routing.local-zone:}") private val localZone: String,
    @Value("\${git.routing.local-region:}") private val localRegion: String
) {
    private val log = KotlinLogging.logger {}

    @EventListener(ApplicationReadyEvent::class)
    fun seed() {
        if (!seedEnabled) return

        ensureLocalNode()

        var created = 0
        repositoryRepository.findAll().forEach { repo ->
            val repoId = repo.id
            if (repoLocationRepository.existsById(repoId)) return@forEach
            repoLocationRepository.save(
                RepoLocationEntity(
                    repoId = repoId,
                    primaryNodeId = localNodeId,
                    updatedAt = LocalDateTime.now()
                )
            )
            created++
        }

        log.info { "[GitRoutingSeeder] seed complete created=$created" }
    }

    private fun ensureLocalNode() {
        val existing = gitNodeRepository.findById(localNodeId).orElse(null)
        if (existing != null) return

        gitNodeRepository.save(
            GitNodeEntity(
                nodeId = localNodeId,
                host = localHost,
                zone = localZone.ifBlank { null },
                region = localRegion.ifBlank { null },
                status = "healthy",
                lastHeartbeatAt = LocalDateTime.now()
            )
        )
    }
}
