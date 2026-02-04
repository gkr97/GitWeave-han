package com.example.gitserver.module.gitindex.storage.infrastructure.routing

import com.example.gitserver.module.gitindex.indexer.infrastructure.routing.GitRoutingMetrics
import com.example.gitserver.module.gitindex.storage.application.routing.GitRoutingPolicy
import com.example.gitserver.module.gitindex.storage.domain.GitNodeEntity
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant
import org.springframework.context.annotation.Profile
import com.example.gitserver.module.gitindex.storage.domain.GitRouteDecision
import com.example.gitserver.module.gitindex.storage.domain.GitRoutingAuditEntity
import com.example.gitserver.module.gitindex.storage.domain.RepoLocationEntity
import com.example.gitserver.module.gitindex.storage.infrastructure.persistence.GitNodeRepository
import com.example.gitserver.module.gitindex.storage.infrastructure.persistence.GitRoutingAuditRepository
import com.example.gitserver.module.gitindex.storage.infrastructure.persistence.RepoLocationRepository
import com.example.gitserver.module.gitindex.storage.infrastructure.persistence.RepoReplicaRepository
import java.util.concurrent.ConcurrentHashMap

@Service
@Profile("gitstorage")
class GitRoutingService(
    private val repoLocationRepository: RepoLocationRepository,
    private val gitNodeRepository: GitNodeRepository,
    private val repoReplicaRepository: RepoReplicaRepository,
    private val auditRepository: GitRoutingAuditRepository,
    private val routingNotifier: GitRoutingNotifier,
    private val metrics: GitRoutingMetrics,
    private val routingPolicy: GitRoutingPolicy,
    private val objectMapper: ObjectMapper,
    @Value("\${git.routing.cache-ttl-ms:3000}") private val cacheTtlMs: Long,
    @Value("\${git.routing.read-max-lag-ms:2000}") private val readMaxLagMs: Long,
    @Value("\${git.routing.read-max-lag-commits:0}") private val readMaxLagCommits: Long,
    @Value("\${git.routing.use-commit-lag:false}") private val useCommitLag: Boolean,
    @Value("\${git.routing.failover-enabled:false}") private val failoverEnabled: Boolean,
    @Value("\${git.routing.failover-max-lag-ms:5000}") private val failoverMaxLagMs: Long,
    @Value("\${git.routing.failover-max-lag-commits:0}") private val failoverMaxLagCommits: Long,
    @Value("\${git.routing.failover-update-enabled:false}") private val failoverUpdateEnabled: Boolean
) {
    private val log = KotlinLogging.logger {}
    private val locationCache = ConcurrentHashMap<Long, CacheEntry<RepoLocationEntity>>()
    private val nodeCache = ConcurrentHashMap<String, CacheEntry<GitNodeEntity>>()

    fun routeForWrite(repoId: Long): GitRouteDecision? {
        val location = loadLocation(repoId, forceFresh = true) ?: return null
        val primary = loadNode(location.primaryNodeId) ?: return null
        if (failoverEnabled && primary.status != "healthy") {
            val failover = selectReplica(
                location,
                maxLagMs = failoverMaxLagMs,
                maxLagCommits = failoverMaxLagCommits
            )
            if (failover != null) {
                if (failoverUpdateEnabled) {
                    updatePrimary(location, failover.nodeId)
                }
                return GitRouteDecision(
                    repoId = repoId,
                    nodeId = failover.nodeId,
                    host = failover.host,
                    role = GitRouteDecision.Role.PRIMARY_FAILOVER,
                    lagMs = failover.lagMetric
                )
            }
        }
        return GitRouteDecision(
            repoId = repoId,
            nodeId = primary.nodeId,
            host = primary.host,
            role = GitRouteDecision.Role.PRIMARY
        )
    }

    fun routeForRead(repoId: Long): GitRouteDecision? {
        val location = loadLocation(repoId, forceFresh = false) ?: return null
        val primary = loadNode(location.primaryNodeId)
        val candidate = selectReplica(
            location,
            maxLagMs = readMaxLagMs,
            maxLagCommits = readMaxLagCommits
        )

        if (candidate != null) {
            return GitRouteDecision(
                repoId = repoId,
                nodeId = candidate.nodeId,
                host = candidate.host,
                role = GitRouteDecision.Role.REPLICA,
                lagMs = candidate.lagMetric
            )
        }

        if (primary == null) {
            log.warn { "[GitRouting] primary node not found repoId=$repoId" }
            return null
        }

        return GitRouteDecision(
            repoId = repoId,
            nodeId = primary.nodeId,
            host = primary.host,
            role = GitRouteDecision.Role.PRIMARY
        )
    }

    private fun loadLocation(repoId: Long, forceFresh: Boolean): RepoLocationEntity? {
        if (!forceFresh) {
            val cached = locationCache[repoId]
            if (cached != null && !cached.isExpired()) {
                return cached.value
            }
        }

        val value = repoLocationRepository.findById(repoId).orElse(null)
        if (value != null) {
            locationCache[repoId] = CacheEntry(value, cacheTtlMs)
        }
        return value
    }

    private fun loadNode(nodeId: String): GitNodeEntity? {
        val cached = nodeCache[nodeId]
        if (cached != null && !cached.isExpired()) {
            return cached.value
        }

        val value = gitNodeRepository.findById(nodeId).orElse(null)
        if (value != null) {
            nodeCache[nodeId] = CacheEntry(value, cacheTtlMs)
        }
        return value
    }

    private fun selectReplica(
        location: RepoLocationEntity,
        maxLagMs: Long,
        maxLagCommits: Long
    ): ReplicaCandidate? {
        val replicas = repoReplicaRepository.findByIdRepoId(location.repoId)
        val states = replicas.mapNotNull { replica ->
            val node = loadNode(replica.id.nodeId) ?: return@mapNotNull null
            GitRoutingPolicy.ReplicaState(
                nodeId = node.nodeId,
                health = replica.health ?: node.status,
                lagMs = replica.lagMs ?: Long.MAX_VALUE,
                lagCommits = replica.lagCommits ?: Long.MAX_VALUE
            )
        }

        val selected = routingPolicy.selectReplica(states, useCommitLag, maxLagMs, maxLagCommits) ?: return null
        val node = loadNode(selected.nodeId) ?: return null
        val lagMetric = if (useCommitLag) selected.lagCommits else selected.lagMs
        return ReplicaCandidate(node.nodeId, node.host, lagMetric)
    }

    private data class ReplicaCandidate(
        val nodeId: String,
        val host: String,
        val lagMetric: Long
    )

    private fun updatePrimary(location: RepoLocationEntity, newPrimaryId: String) {
        if (location.primaryNodeId == newPrimaryId) return
        val oldPrimary = location.primaryNodeId
        repoReplicaRepository.deleteByIdRepoIdAndIdNodeId(location.repoId, newPrimaryId)
        if (repoReplicaRepository.findByIdRepoIdAndIdNodeId(location.repoId, oldPrimary) == null) {
            repoReplicaRepository.save(
                com.example.gitserver.module.gitindex.storage.domain.RepoReplicaEntity(
                    id = com.example.gitserver.module.gitindex.storage.domain.RepoReplicaId(
                        repoId = location.repoId,
                        nodeId = oldPrimary
                    ),
                    health = "unknown",
                    lagMs = null,
                    lagCommits = null
                )
            )
        }

        location.primaryNodeId = newPrimaryId
        location.updatedAt = java.time.LocalDateTime.now()
        repoLocationRepository.save(location)
        locationCache[location.repoId] = CacheEntry(location, cacheTtlMs)

        auditRepository.save(
            GitRoutingAuditEntity(
                actor = "system/failover",
                action = "FAILOVER_PROMOTE_PRIMARY",
                repoId = location.repoId,
                nodeId = newPrimaryId,
                payload = objectMapper.writeValueAsString(
                    mapOf(
                        "oldPrimary" to oldPrimary,
                        "newPrimary" to newPrimaryId
                    )
                )
            )
        )
        metrics.recordFailover()
        routingNotifier.notifyFailover(location.repoId, oldPrimary, newPrimaryId)
    }

    private class CacheEntry<T>(
        val value: T,
        ttlMs: Long
    ) {
        private val expiresAt = Instant.now().toEpochMilli() + ttlMs
        fun isExpired(): Boolean = Instant.now().toEpochMilli() > expiresAt
    }
}
