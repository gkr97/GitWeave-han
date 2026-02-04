package com.example.gitserver.module.gitindex.storage.application.routing

import com.example.gitserver.module.gitindex.storage.domain.GitNodeEntity
import com.example.gitserver.module.gitindex.storage.domain.RepoLocationEntity
import com.example.gitserver.module.gitindex.storage.domain.RepoReplicaEntity
import com.example.gitserver.module.gitindex.storage.domain.RepoReplicaId
import com.example.gitserver.module.gitindex.storage.infrastructure.persistence.GitNodeRepository
import com.example.gitserver.module.gitindex.storage.infrastructure.persistence.RepoLocationRepository
import com.example.gitserver.module.gitindex.storage.infrastructure.persistence.RepoReplicaRepository
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class RepoPlacementService(
    private val repoLocationRepository: RepoLocationRepository,
    private val gitNodeRepository: GitNodeRepository,
    private val repoReplicaRepository: RepoReplicaRepository,
    @Value("\${git.routing.placement.enabled:true}") private val enabled: Boolean,
    @Value("\${git.routing.local-node-id:local}") private val localNodeId: String,
    @Value("\${git.routing.local-host:localhost:8080}") private val localHost: String,
    @Value("\${git.routing.local-zone:}") private val localZone: String,
    @Value("\${git.routing.local-region:}") private val localRegion: String,
    @Value("\${git.routing.placement.repo-count-weight:1.0}") private val repoCountWeight: Double,
    @Value("\${git.routing.placement.disk-weight:1.0}") private val diskWeight: Double,
    @Value("\${git.routing.placement.iops-weight:0.1}") private val iopsWeight: Double,
    @Value("\${git.routing.placement.replica-count:1}") private val replicaCount: Int,
    @Value("\${git.routing.placement.prefer-same-region:true}") private val preferSameRegion: Boolean,
    @Value("\${git.routing.placement.prefer-same-zone:false}") private val preferSameZone: Boolean,
    @Value("\${git.routing.default-replica-node-ids:}") private val defaultReplicaNodeIdsRaw: String
) {
    private val log = KotlinLogging.logger {}

    fun assignRepository(repoId: Long) {
        if (!enabled) return
        if (repoLocationRepository.existsById(repoId)) return

        val primary = selectPrimaryNode(emptySet())
        if (primary == null) {
            log.warn { "[RepoPlacement] no nodes available repoId=$repoId" }
            return
        }

        val replicas = chooseReplicas(primary.nodeId)

        val entity = RepoLocationEntity(
            repoId = repoId,
            primaryNodeId = primary.nodeId,
            updatedAt = LocalDateTime.now()
        )
        repoLocationRepository.save(entity)
        if (replicas.isNotEmpty()) {
            val now = LocalDateTime.now()
            val replicaEntities = replicas.map { nodeId ->
                RepoReplicaEntity(
                    id = RepoReplicaId(repoId = repoId, nodeId = nodeId),
                    health = "unknown",
                    lagMs = null,
                    lagCommits = null,
                    updatedAt = now
                )
            }
            repoReplicaRepository.saveAll(replicaEntities)
        }
        log.info { "[RepoPlacement] assigned repoId=$repoId primary=${primary.nodeId} replicas=${replicas.size}" }
    }

    fun selectPrimaryNode(excludeIds: Set<String>): GitNodeEntity? {
        val nodes = gitNodeRepository.findAll()
            .filter { it.status == "healthy" && !excludeIds.contains(it.nodeId) }
        if (nodes.isEmpty()) {
            val local = gitNodeRepository.findById(localNodeId).orElse(null)
            return local ?: gitNodeRepository.save(
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

        return nodes.minByOrNull { score(it) }
    }

    private fun score(node: GitNodeEntity): Double {
        val repoCount = (node.repoCount ?: 0).toDouble()
        val diskUsage = (node.diskUsagePct?.toDouble() ?: 0.0)
        val iops = (node.iops ?: 0).toDouble()
        return repoCountWeight * repoCount + diskWeight * diskUsage + iopsWeight * iops
    }

    private fun chooseReplicas(primaryNodeId: String): List<String> {
        val configured = parseList(defaultReplicaNodeIdsRaw)
            .filter { it != primaryNodeId }
            .distinct()
        if (configured.isNotEmpty()) return configured

        val primary = gitNodeRepository.findById(primaryNodeId).orElse(null)
        val candidates = gitNodeRepository.findAll()
            .filter { it.status == "healthy" && it.nodeId != primaryNodeId }
            .sortedWith(
                compareBy<GitNodeEntity> { preferenceRank(primary, it) }
                    .thenBy { score(it) }
            )
        return candidates.take(replicaCount.coerceAtLeast(0)).map { it.nodeId }
    }

    private fun preferenceRank(primary: GitNodeEntity?, candidate: GitNodeEntity): Int {
        if (primary == null) return 2
        val sameZone = !primary.zone.isNullOrBlank() && primary.zone == candidate.zone
        val sameRegion = !primary.region.isNullOrBlank() && primary.region == candidate.region
        return when {
            preferSameZone && sameZone -> 0
            preferSameRegion && sameRegion -> 1
            else -> 2
        }
    }

    private fun parseList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(",")
            .mapNotNull { it.trim().takeIf { t -> t.isNotEmpty() } }
    }
}
