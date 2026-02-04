package com.example.gitserver.module.gitindex.storage.infrastructure.routing

import com.example.gitserver.module.gitindex.indexer.infrastructure.routing.GitReplicationTaskEntity
import com.example.gitserver.module.gitindex.indexer.infrastructure.routing.GitReplicationTaskRepository
import com.example.gitserver.module.gitindex.storage.infrastructure.persistence.RepoLocationRepository
import com.example.gitserver.module.gitindex.storage.infrastructure.persistence.RepoReplicaRepository
import com.example.gitserver.module.repository.domain.event.RepositoryPushed
import com.example.gitserver.module.repository.infrastructure.persistence.BranchRepository
import mu.KotlinLogging
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import org.springframework.context.annotation.Profile

@Component
@Profile("gitstorage")
class GitReplicationEnqueuer(
    private val repoLocationRepository: RepoLocationRepository,
    private val repoReplicaRepository: RepoReplicaRepository,
    private val taskRepository: GitReplicationTaskRepository,
    private val branchRepository: BranchRepository,
    private val trafficTracker: GitRepoTrafficTracker
) {
    private val log = KotlinLogging.logger {}

    @EventListener
    fun onRepositoryPushed(e: RepositoryPushed) {
        val location = repoLocationRepository.findById(e.repositoryId).orElse(null) ?: return
        val replicas = repoReplicaRepository.findByIdRepoId(location.repoId)
            .map { it.id.nodeId }
        if (replicas.isEmpty()) return

        val priority = computePriority(e.repositoryId)
        replicas.forEach { replicaId ->
            val exists = taskRepository.existsByRepoIdAndTargetNodeIdAndStatusIn(
                e.repositoryId,
                replicaId,
                listOf("PENDING", "RUNNING")
            )
            if (exists) return@forEach

            taskRepository.save(
                GitReplicationTaskEntity(
                    repoId = e.repositoryId,
                    sourceNodeId = location.primaryNodeId,
                    targetNodeId = replicaId,
                    status = "PENDING",
                    priority = priority
                )
            )
        }

        log.info { "[ReplicationEnqueue] repoId=${e.repositoryId} tasks=${replicas.size}" }
    }

    private fun computePriority(repoId: Long): Int {
        val branch = branchRepository.findAllByRepositoryId(repoId)
            .firstOrNull { it.isDefault }
        val last = branch?.lastCommitAt ?: return 1
        val minutes = Duration.between(last, LocalDateTime.now()).toMinutes()
        val traffic = trafficTracker.getAndReset(repoId)
        val trafficBoost = when {
            traffic >= 100 -> 5
            traffic >= 20 -> 2
            else -> 0
        }
        val base = when {
            minutes <= 5 -> 10
            minutes <= 60 -> 5
            else -> 1
        }
        return (base + trafficBoost).coerceAtMost(20)
    }
}
