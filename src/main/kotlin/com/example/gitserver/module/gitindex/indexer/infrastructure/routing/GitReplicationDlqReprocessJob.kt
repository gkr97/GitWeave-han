package com.example.gitserver.module.gitindex.indexer.infrastructure.routing

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import org.springframework.context.annotation.Profile

@Component
@Profile("gitstorage")
class GitReplicationDlqReprocessJob(
    private val dlqRepository: GitReplicationDlqRepository,
    private val taskRepository: GitReplicationTaskRepository,
    private val notifier: GitReplicationNotifier,
    @Value("\${git.replication.dlq-reprocess-enabled:false}") private val enabled: Boolean,
    @Value("\${git.replication.dlq-reprocess-max:50}") private val maxPerRun: Int
) {
    private val log = KotlinLogging.logger {}

    @Scheduled(fixedDelayString = "\${git.replication.dlq-reprocess-interval-ms:30000}")
    fun reprocess() {
        if (!enabled) return

        val items = dlqRepository.findAll().take(maxPerRun)
        items.forEach { dlq ->
            runCatching {
                taskRepository.save(
                    GitReplicationTaskEntity(
                        repoId = dlq.repoId,
                        sourceNodeId = dlq.sourceNodeId,
                        targetNodeId = dlq.targetNodeId,
                        status = "PENDING",
                        priority = 1,
                        attempt = 0,
                        lastError = null,
                        updatedAt = LocalDateTime.now()
                    )
                )
                dlqRepository.delete(dlq)
            }.onFailure { e ->
                notifier.notifyDlqReprocessFailure(dlq.id, e.message)
            }
        }

        if (items.isNotEmpty()) {
            log.info { "[ReplicationDLQ] reprocessed=${items.size}" }
        }
    }
}
