package com.example.gitserver.module.gitindex.storage.infrastructure.routing

import com.example.gitserver.module.gitindex.storage.domain.GitNodeEntity
import com.example.gitserver.module.gitindex.storage.domain.RepoLocationEntity
import com.example.gitserver.module.gitindex.storage.domain.RepoReplicaEntity
import com.example.gitserver.module.gitindex.storage.domain.RepoReplicaId
import com.example.gitserver.module.gitindex.storage.infrastructure.persistence.GitNodeRepository
import com.example.gitserver.module.gitindex.storage.infrastructure.persistence.RepoLocationRepository
import com.example.gitserver.module.gitindex.storage.infrastructure.persistence.RepoReplicaRepository
import com.example.gitserver.module.repository.domain.event.RepositoryCreated
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import org.springframework.context.annotation.Profile

@Component
@Profile("gitstorage")
class GitReplicaAutoProvisioner(
    private val repoLocationRepository: RepoLocationRepository,
    private val gitNodeRepository: GitNodeRepository,
    private val repoReplicaRepository: RepoReplicaRepository,
    @Value("\${git.routing.seed-local:false}") private val seedEnabled: Boolean,
    @Value("\${git.routing.local-node-id:local}") private val localNodeId: String,
    @Value("\${git.routing.local-host:localhost:8080}") private val localHost: String,
    @Value("\${git.routing.local-zone:}") private val localZone: String,
    @Value("\${git.routing.local-region:}") private val localRegion: String,
    @Value("\${git.routing.seed-replica-node-ids:}") private val replicaNodeIdsRaw: String,
    @Value("\${git.routing.seed-replica-add-existing:true}") private val addExisting: Boolean,
    @Value("\${git.routing.seed-replica-heartbeat-enabled:true}") private val heartbeatEnabled: Boolean
) {
    private val log = KotlinLogging.logger {}

    @EventListener(ApplicationReadyEvent::class)
    fun seedReplicas() {
        if (!seedEnabled) return
        val replicas = parseList(replicaNodeIdsRaw)
        if (replicas.isEmpty()) return

        ensureReplicaNodes(replicas)

        if (!addExisting) return
        repoLocationRepository.findAll().forEach { location ->
            val updated = addReplicas(location, replicas)
            if (updated) repoLocationRepository.save(location)
        }
        log.info { "[ReplicaSeed] replicas=${replicas.size} addExisting=$addExisting" }
    }

    @Scheduled(fixedDelayString = "\${git.routing.seed-replica-heartbeat-interval-ms:5000}")
    fun heartbeatReplicas() {
        if (!seedEnabled || !heartbeatEnabled) return
        val replicas = parseList(replicaNodeIdsRaw)
        if (replicas.isEmpty()) return
        val now = LocalDateTime.now()
        replicas.forEach { nodeId ->
            val node = gitNodeRepository.findById(nodeId).orElse(null)
            if (node == null) {
                gitNodeRepository.save(
                    GitNodeEntity(
                        nodeId = nodeId,
                        host = localHost,
                        zone = localZone.ifBlank { null },
                        region = localRegion.ifBlank { null },
                        status = "healthy",
                        lastHeartbeatAt = now
                    )
                )
            } else {
                node.status = "healthy"
                node.lastHeartbeatAt = now
                node.updatedAt = now
                gitNodeRepository.save(node)
            }
        }
    }

    @EventListener
    fun onRepositoryCreated(e: RepositoryCreated) {
        val replicas = parseList(replicaNodeIdsRaw)
        if (replicas.isEmpty()) return

        ensureReplicaNodes(replicas)

        val location = repoLocationRepository.findById(e.repositoryId).orElse(null)
        if (location == null) {
            repoLocationRepository.save(
                RepoLocationEntity(
                    repoId = e.repositoryId,
                    primaryNodeId = localNodeId,
                    updatedAt = LocalDateTime.now()
                )
            )
            upsertReplicaRows(e.repositoryId, replicas)
            log.info { "[ReplicaSeed] created RepoLocation repoId=${e.repositoryId}" }
            return
        }

        val updated = addReplicas(location, replicas)
        if (updated) {
            repoLocationRepository.save(location)
            upsertReplicaRows(e.repositoryId, replicas)
            log.info { "[ReplicaSeed] updated RepoLocation repoId=${e.repositoryId}" }
        }
    }

    private fun ensureReplicaNodes(replicas: List<String>) {
        replicas.forEach { nodeId ->
            val existing = gitNodeRepository.findById(nodeId).orElse(null)
            if (existing != null) return@forEach
            gitNodeRepository.save(
                GitNodeEntity(
                    nodeId = nodeId,
                    host = localHost,
                    zone = localZone.ifBlank { null },
                    region = localRegion.ifBlank { null },
                    status = "healthy",
                    lastHeartbeatAt = LocalDateTime.now()
                )
            )
        }
    }

    private fun addReplicas(location: RepoLocationEntity, replicas: List<String>): Boolean {
        val existing = repoReplicaRepository.findByIdRepoId(location.repoId)
            .map { it.id.nodeId }
            .toSet()
        val toAdd = replicas.filterNot { existing.contains(it) }
        if (toAdd.isEmpty()) return false
        upsertReplicaRows(location.repoId, toAdd)
        location.updatedAt = LocalDateTime.now()
        return true
    }

    private fun upsertReplicaRows(repoId: Long, replicas: List<String>) {
        val now = LocalDateTime.now()
        val existing = repoReplicaRepository.findByIdRepoId(repoId)
            .map { it.id.nodeId }
            .toSet()
        val toCreate = replicas.filterNot { existing.contains(it) }
            .map { nodeId ->
                RepoReplicaEntity(
                    id = RepoReplicaId(repoId = repoId, nodeId = nodeId),
                    health = "unknown",
                    lagMs = null,
                    lagCommits = null,
                    updatedAt = now
                )
            }
        if (toCreate.isNotEmpty()) repoReplicaRepository.saveAll(toCreate)
    }

    private fun parseList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(",")
            .mapNotNull { it.trim().takeIf { t -> t.isNotEmpty() } }
    }
}
