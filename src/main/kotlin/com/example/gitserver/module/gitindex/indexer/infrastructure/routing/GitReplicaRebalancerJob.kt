package com.example.gitserver.module.gitindex.indexer.infrastructure.routing

import com.example.gitserver.module.gitindex.storage.infrastructure.persistence.GitNodeRepository
import com.example.gitserver.module.gitindex.storage.domain.RepoLocationEntity
import com.example.gitserver.module.gitindex.storage.domain.RepoReplicaEntity
import com.example.gitserver.module.gitindex.storage.domain.RepoReplicaId
import com.example.gitserver.module.gitindex.storage.infrastructure.persistence.RepoLocationRepository
import com.example.gitserver.module.gitindex.storage.infrastructure.persistence.RepoReplicaRepository
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import org.springframework.context.annotation.Profile

@Component
@Profile("gitstorage")
class GitReplicaRebalancerJob(
    private val gitNodeRepository: GitNodeRepository,
    private val repoLocationRepository: RepoLocationRepository,
    private val repoReplicaRepository: RepoReplicaRepository,
    private val taskRepository: GitReplicationTaskRepository,
    @Value("\${git.rebalance.enabled:false}") private val enabled: Boolean,
    @Value("\${git.rebalance.disk-threshold:80}") private val diskThreshold: Double,
    @Value("\${git.rebalance.repo-threshold:1000}") private val repoThreshold: Int,
    @Value("\${git.rebalance.iops-threshold:2000}") private val iopsThreshold: Int,
    @Value("\${git.rebalance.batch-size:20}") private val batchSize: Int,
    @Value("\${git.rebalance.promote-primary:false}") private val promotePrimary: Boolean,
    @Value("\${git.rebalance.promote-max-lag-commits:0}") private val promoteMaxLagCommits: Long
) {
    private val log = KotlinLogging.logger {}

    @Scheduled(fixedDelayString = "\${git.rebalance.interval-ms:60000}")
    fun rebalance() {
        if (!enabled) return

        val nodes = gitNodeRepository.findAll().filter { it.status == "healthy" }
        if (nodes.size < 2) return

        val overloaded = nodes.filter {
            (it.diskUsagePct?.toDouble() ?: 0.0) >= diskThreshold ||
                (it.repoCount ?: 0) >= repoThreshold ||
                (it.iops ?: 0) >= iopsThreshold
        }
        val candidates = nodes.sortedBy { it.repoCount ?: 0 }

        overloaded.forEach { sourceNode ->
            val targets = candidates.filter { it.nodeId != sourceNode.nodeId }
            if (targets.isEmpty()) return@forEach

            val locations = repoLocationRepository.findAll()
                .filter { it.primaryNodeId == sourceNode.nodeId }
                .take(batchSize)

            locations.forEach { location ->
                val target = targets.firstOrNull() ?: return@forEach
                val existing = repoReplicaRepository.findByIdRepoIdAndIdNodeId(location.repoId, target.nodeId)
                if (existing != null) return@forEach

                repoReplicaRepository.save(
                    RepoReplicaEntity(
                        id = RepoReplicaId(repoId = location.repoId, nodeId = target.nodeId),
                        health = "unknown",
                        lagMs = null,
                        lagCommits = null,
                        updatedAt = LocalDateTime.now()
                    )
                )
                location.updatedAt = LocalDateTime.now()
                repoLocationRepository.save(location)

                if (!taskRepository.existsByRepoIdAndTargetNodeIdAndStatusIn(
                        location.repoId,
                        target.nodeId,
                        listOf("PENDING", "RUNNING")
                    )
                ) {
                    taskRepository.save(
                        GitReplicationTaskEntity(
                            repoId = location.repoId,
                            sourceNodeId = location.primaryNodeId,
                            targetNodeId = target.nodeId,
                            status = "PENDING",
                            priority = 1
                        )
                    )
                }

                if (promotePrimary && canPromote(location, target.nodeId)) {
                    promote(location, target.nodeId)
                }
            }

            log.info { "[Rebalance] source=${sourceNode.nodeId} moved=${locations.size}" }
        }
    }

    private fun canPromote(location: RepoLocationEntity, nodeId: String): Boolean {
        val replica = repoReplicaRepository.findByIdRepoIdAndIdNodeId(location.repoId, nodeId)
        val lag = replica?.lagCommits ?: Long.MAX_VALUE
        val health = replica?.health ?: "unknown"
        return health == "healthy" && lag <= promoteMaxLagCommits
    }

    private fun promote(location: RepoLocationEntity, nodeId: String) {
        repoReplicaRepository.deleteByIdRepoIdAndIdNodeId(location.repoId, nodeId)
        if (repoReplicaRepository.findByIdRepoIdAndIdNodeId(location.repoId, location.primaryNodeId) == null) {
            repoReplicaRepository.save(
                RepoReplicaEntity(
                    id = RepoReplicaId(repoId = location.repoId, nodeId = location.primaryNodeId),
                    health = "unknown",
                    lagMs = null,
                    lagCommits = null,
                    updatedAt = LocalDateTime.now()
                )
            )
        }
        location.primaryNodeId = nodeId
        location.updatedAt = LocalDateTime.now()
        repoLocationRepository.save(location)
        log.info { "[Rebalance] promote primary repo=${location.repoId} newPrimary=$nodeId" }
    }
}
