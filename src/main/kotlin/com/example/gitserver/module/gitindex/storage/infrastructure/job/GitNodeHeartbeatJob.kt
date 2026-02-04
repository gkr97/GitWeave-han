package com.example.gitserver.module.gitindex.storage.infrastructure.job

import com.example.gitserver.module.gitindex.storage.domain.GitNodeEntity
import com.example.gitserver.module.gitindex.storage.infrastructure.persistence.GitNodeRepository
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import org.springframework.context.annotation.Profile

@Component
@Profile("gitstorage")
class GitNodeHeartbeatJob(
    private val gitNodeRepository: GitNodeRepository,
    @Value("\${git.routing.local-node-id:local}") private val localNodeId: String,
    @Value("\${git.routing.local-host:localhost:8080}") private val localHost: String,
    @Value("\${git.routing.local-zone:}") private val localZone: String,
    @Value("\${git.routing.local-region:}") private val localRegion: String
) {
    private val log = KotlinLogging.logger {}

    @Scheduled(fixedDelayString = "\${git.routing.heartbeat-interval-ms:5000}")
    fun heartbeat() {
        val now = LocalDateTime.now()
        val existing = gitNodeRepository.findById(localNodeId).orElse(null)
        if (existing == null) {
            gitNodeRepository.save(
                GitNodeEntity(
                    nodeId = localNodeId,
                    host = localHost,
                    zone = localZone.ifBlank { null },
                    region = localRegion.ifBlank { null },
                    status = "healthy",
                    lastHeartbeatAt = now
                )
            )
            log.info { "[GitNodeHeartbeat] created node=$localNodeId host=$localHost" }
            return
        }

        existing.host = localHost
        existing.zone = localZone.ifBlank { null }
        existing.region = localRegion.ifBlank { null }
        existing.status = "healthy"
        existing.lastHeartbeatAt = now
        gitNodeRepository.save(existing)
    }
}
