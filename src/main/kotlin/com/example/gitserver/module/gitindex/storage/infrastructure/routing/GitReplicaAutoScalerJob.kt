package com.example.gitserver.module.gitindex.storage.infrastructure.routing

import com.example.gitserver.module.gitindex.storage.domain.GitNodeEntity
import com.example.gitserver.module.gitindex.storage.domain.RepoLocationEntity
import com.example.gitserver.module.gitindex.storage.domain.RepoReplicaEntity
import com.example.gitserver.module.gitindex.storage.domain.RepoReplicaId
import com.example.gitserver.module.gitindex.storage.infrastructure.persistence.GitNodeRepository
import com.example.gitserver.module.gitindex.storage.infrastructure.persistence.RepoLocationRepository
import com.example.gitserver.module.gitindex.storage.infrastructure.persistence.RepoReplicaRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryStatsRepository
import mu.KotlinLogging
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import org.springframework.context.annotation.Profile

@Component
@Profile("gitstorage")
class GitReplicaAutoScalerJob(
    private val repoLocationRepository: RepoLocationRepository,
    private val gitNodeRepository: GitNodeRepository,
    private val repoReplicaRepository: RepoReplicaRepository,
    private val trafficTracker: GitRepoTrafficTracker,
    private val statsProvider: ObjectProvider<RepositoryStatsRepository>,
    @Value("\${git.routing.autoscale.enabled:false}") private val enabled: Boolean,
    @Value("\${git.routing.autoscale.interval-ms:60000}") private val intervalMs: Long,
    @Value("\${git.routing.autoscale.min-replicas:1}") private val minReplicas: Int,
    @Value("\${git.routing.autoscale.max-replicas:3}") private val maxReplicas: Int,
    @Value("\${git.routing.autoscale.scale-down-enabled:false}") private val scaleDownEnabled: Boolean,
    @Value("\${git.routing.autoscale.traffic-thresholds:20,100,300}") private val trafficThresholdsRaw: String,
    @Value("\${git.routing.autoscale.stars-thresholds:50,200,1000}") private val starsThresholdsRaw: String,
    @Value("\${git.routing.autoscale.watchers-thresholds:50,200,1000}") private val watchersThresholdsRaw: String,
    @Value("\${git.routing.autoscale.prefer-same-region:true}") private val preferSameRegion: Boolean,
    @Value("\${git.routing.autoscale.prefer-same-zone:false}") private val preferSameZone: Boolean
) {
    private val log = KotlinLogging.logger {}

    @Scheduled(fixedDelayString = "\${git.routing.autoscale.interval-ms:60000}")
    fun autoscale() {
        if (!enabled) return

        val trafficSnapshot = trafficTracker.snapshotAndResetAll()

        val statsRepo = statsProvider.ifAvailable
        val thresholdsTraffic = parseThresholds(trafficThresholdsRaw)
        val thresholdsStars = parseThresholds(starsThresholdsRaw)
        val thresholdsWatchers = parseThresholds(watchersThresholdsRaw)

        repoLocationRepository.findAll().forEach { location ->
            val repoId = location.repoId
            val traffic = trafficSnapshot[repoId] ?: 0L
            val location = repoLocationRepository.findById(repoId).orElse(null) ?: return@forEach
            val current = repoReplicaRepository.findByIdRepoId(repoId).map { it.id.nodeId }.toMutableList()
            val stats = statsRepo?.findById(repoId)?.orElse(null)
            val desired = desiredReplicaCount(
                traffic,
                stats?.stars ?: 0,
                stats?.watchers ?: 0,
                thresholdsTraffic,
                thresholdsStars,
                thresholdsWatchers
            )

            if (desired > current.size) {
                val added = addReplicas(location, current, desired - current.size)
                if (added) repoLocationRepository.save(location)
            } else if (scaleDownEnabled && desired < current.size) {
                val removed = removeReplicas(location, current, current.size - desired)
                if (removed) repoLocationRepository.save(location)
            }
        }
    }

    private fun desiredReplicaCount(
        traffic: Long,
        stars: Int,
        watchers: Int,
        trafficThresholds: List<Int>,
        starsThresholds: List<Int>,
        watchersThresholds: List<Int>
    ): Int {
        val trafficBoost = trafficThresholds.count { traffic >= it }
        val starBoost = starsThresholds.count { stars >= it }
        val watcherBoost = watchersThresholds.count { watchers >= it }
        val raw = minReplicas + trafficBoost + starBoost + watcherBoost
        return raw.coerceIn(minReplicas, maxReplicas)
    }

    private fun addReplicas(location: RepoLocationEntity, current: MutableList<String>, count: Int): Boolean {
        val candidates = candidateNodes(location.primaryNodeId, current)
        if (candidates.isEmpty()) return false

        val toAdd = candidates.take(count).map { it.nodeId }
        if (toAdd.isEmpty()) return false
        current.addAll(toAdd)
        val now = LocalDateTime.now()
        val rows = toAdd.map { nodeId ->
            RepoReplicaEntity(
                id = RepoReplicaId(repoId = location.repoId, nodeId = nodeId),
                health = "unknown",
                lagMs = null,
                lagCommits = null,
                updatedAt = now
            )
        }
        repoReplicaRepository.saveAll(rows)
        location.updatedAt = LocalDateTime.now()
        log.info { "[AutoScale] add repo=${location.repoId} replicas=${toAdd.size}" }
        return true
    }

    private fun removeReplicas(location: RepoLocationEntity, current: MutableList<String>, count: Int): Boolean {
        if (current.isEmpty()) return false
        val primary = gitNodeRepository.findById(location.primaryNodeId).orElse(null)
        val currentNodes = gitNodeRepository.findAll()
            .filter { current.contains(it.nodeId) }
            .sortedWith(
                compareBy<GitNodeEntity> { preferenceRank(primary, it) }
                    .thenBy { score(it) }
            )
        val toRemove = currentNodes.reversed().take(count).map { it.nodeId }
        if (toRemove.isEmpty()) return false
        current.removeAll(toRemove.toSet())
        toRemove.forEach { nodeId ->
            repoReplicaRepository.deleteByIdRepoIdAndIdNodeId(location.repoId, nodeId)
        }
        location.updatedAt = LocalDateTime.now()
        log.info { "[AutoScale] remove repo=${location.repoId} replicas=${toRemove.size}" }
        return true
    }

    private fun candidateNodes(primaryNodeId: String, current: List<String>): List<GitNodeEntity> {
        val primary = gitNodeRepository.findById(primaryNodeId).orElse(null)
        val nodes = gitNodeRepository.findAll().filter { it.status == "healthy" }
            .filter { it.nodeId != primaryNodeId && !current.contains(it.nodeId) }

        return nodes.sortedWith(
            compareBy<GitNodeEntity> { preferenceRank(primary, it) }
                .thenBy { score(it) }
        )
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

    private fun score(node: GitNodeEntity): Double {
        val repoCount = (node.repoCount ?: 0).toDouble()
        val diskUsage = (node.diskUsagePct?.toDouble() ?: 0.0)
        val iops = (node.iops ?: 0).toDouble()
        return repoCount * 1.0 + diskUsage * 1.0 + iops * 0.1
    }

    private fun parseThresholds(raw: String): List<Int> =
        raw.split(",").mapNotNull { it.trim().toIntOrNull() }.sorted()

}
