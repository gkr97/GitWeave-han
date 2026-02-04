package com.example.gitserver.module.gitindex.indexer.infrastructure.routing

import com.example.gitserver.module.gitindex.indexer.application.ReplicationService
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import jakarta.annotation.PreDestroy
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.springframework.context.annotation.Profile

@Component
@Profile("gitstorage")
class GitReplicationWorker(
    private val taskRepository: GitReplicationTaskRepository,
    private val replicationService: ReplicationService,
    private val metrics: GitRoutingMetrics,
    @Value("\${git.replication.enabled:false}") private val enabled: Boolean,
    @Value("\${git.replication.worker-parallelism:4}") private val parallelism: Int,
    @Value("\${git.replication.worker-node-id:}") private val workerNodeId: String,
    @Value("\${git.routing.local-node-id:local}") private val localNodeId: String
) {
    private val log = KotlinLogging.logger {}
    private val pool: ExecutorService = Executors.newFixedThreadPool(parallelism.coerceAtLeast(1))

    @Scheduled(fixedDelayString = "\${git.replication.poll-interval-ms:3000}")
    fun work() {
        if (!enabled) return

        val nodeId = workerNodeId.ifBlank { localNodeId }
        val tasks = taskRepository.findTop100ByStatusAndTargetNodeIdOrderByPriorityDescCreatedAtAsc(
            "PENDING",
            nodeId
        )
        if (tasks.isEmpty()) return
        metrics.updatePendingTasks(tasks.size.toLong())

        try {
            tasks.forEach { task ->
                pool.submit { processTask(task) }
            }
        } finally {
            metrics.updatePendingTasks(0)
        }
    }

    private fun processTask(task: GitReplicationTaskEntity) {
        if (!replicationService.claimTask(task.id)) return
        replicationService.processTask(task)
        log.info { "[ReplicationWorker] task=${task.id} status=${task.status}" }
    }

    @PreDestroy
    fun shutdownPool() {
        pool.shutdown()
        pool.awaitTermination(10, TimeUnit.SECONDS)
    }
}
