package com.example.gitserver.module.gitindex.storage.infrastructure.job

import com.example.gitserver.module.gitindex.storage.infrastructure.persistence.GitNodeRepository
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import org.springframework.context.annotation.Profile

@Component
@Profile("gitstorage")
class GitNodeHealthMonitorJob(
    private val gitNodeRepository: GitNodeRepository,
    @Value("\${git.routing.node-health-timeout-ms:15000}") private val timeoutMs: Long
) {
    private val log = KotlinLogging.logger {}

    @Scheduled(fixedDelayString = "\${git.routing.node-health-interval-ms:5000}")
    fun checkHealth() {
        val now = LocalDateTime.now()
        val timeout = Duration.ofMillis(timeoutMs)

        gitNodeRepository.findAll().forEach { node ->
            val last = node.lastHeartbeatAt
            if (last == null) return@forEach
            val isStale = Duration.between(last, now) > timeout
            val newStatus = if (isStale) "down" else "healthy"
            if (node.status != newStatus) {
                node.status = newStatus
                node.updatedAt = now
                gitNodeRepository.save(node)
                log.info { "[GitNodeHealth] node=${node.nodeId} status=${node.status}" }
            }
        }
    }
}
