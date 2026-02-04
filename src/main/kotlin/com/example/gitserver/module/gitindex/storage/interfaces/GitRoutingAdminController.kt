package com.example.gitserver.module.gitindex.storage.interfaces

import com.example.gitserver.module.gitindex.storage.domain.GitNodeEntity
import com.example.gitserver.module.gitindex.storage.infrastructure.persistence.GitNodeRepository
import com.example.gitserver.module.gitindex.storage.domain.GitRoutingAuditEntity
import com.example.gitserver.module.gitindex.storage.infrastructure.persistence.GitRoutingAuditRepository
import com.example.gitserver.module.gitindex.storage.domain.RepoLocationEntity
import com.example.gitserver.module.gitindex.storage.domain.RepoReplicaEntity
import com.example.gitserver.module.gitindex.storage.domain.RepoReplicaId
import com.example.gitserver.module.gitindex.storage.infrastructure.persistence.RepoLocationRepository
import com.example.gitserver.module.gitindex.storage.infrastructure.persistence.RepoReplicaRepository
import com.example.gitserver.module.gitindex.storage.infrastructure.routing.GitReplicaBackfillJob
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
import org.springframework.context.annotation.Profile

@RestController
@RequestMapping("/internal/git/routing")
@Profile("gitstorage")
class GitRoutingAdminController(
    private val repoLocationRepository: RepoLocationRepository,
    private val gitNodeRepository: GitNodeRepository,
    private val repoReplicaRepository: RepoReplicaRepository,
    private val backfillJob: GitReplicaBackfillJob,
    private val jdbcTemplate: JdbcTemplate,
    private val auditRepository: GitRoutingAuditRepository,
    private val objectMapper: ObjectMapper,
    @Value("\${git.routing.admin-key:}") private val adminKey: String
) {

    @GetMapping("/repo/{repoId}")
    fun getRepoLocation(
        @PathVariable repoId: Long,
        @RequestHeader(value = "X-Git-Admin-Key", required = false) providedKey: String?
    ): ResponseEntity<RepoLocationEntity> {
        if (!authorized(providedKey)) return ResponseEntity.status(401).build()
        val entity = repoLocationRepository.findById(repoId).orElse(null) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(entity)
    }

    @PostMapping("/repo/{repoId}")
    @Transactional
    fun upsertRepoLocation(
        @PathVariable repoId: Long,
        @RequestBody req: RepoLocationUpsertRequest,
        @RequestHeader(value = "X-Git-Admin-Key", required = false) providedKey: String?,
        @RequestHeader(value = "X-Git-Admin-Actor", required = false) actor: String?
    ): ResponseEntity<RepoLocationEntity> {
        if (!authorized(providedKey)) return ResponseEntity.status(401).build()

        val now = LocalDateTime.now()
        val existing = repoLocationRepository.findById(repoId).orElse(null)
        val entity = if (existing == null) {
            RepoLocationEntity(
                repoId = repoId,
                primaryNodeId = req.primaryNodeId,
                lastWriteCommit = req.lastWriteCommit,
                updatedAt = now
            )
        } else {
            existing.primaryNodeId = req.primaryNodeId
            existing.lastWriteCommit = req.lastWriteCommit
            existing.updatedAt = now
            existing
        }

        val saved = repoLocationRepository.save(entity)
        syncReplicas(repoId, req)
        auditRepository.save(
            GitRoutingAuditEntity(
                actor = actor,
                action = "UPSERT_REPO_LOCATION",
                repoId = repoId,
                payload = objectMapper.writeValueAsString(req)
            )
        )
        return ResponseEntity.ok(saved)
    }

    @PostMapping("/repo/{repoId}/lag")
    @Transactional
    fun updateReplicaLag(
        @PathVariable repoId: Long,
        @RequestBody req: ReplicaLagUpdateRequest,
        @RequestHeader(value = "X-Git-Admin-Key", required = false) providedKey: String?,
        @RequestHeader(value = "X-Git-Admin-Actor", required = false) actor: String?
    ): ResponseEntity<RepoLocationEntity> {
        if (!authorized(providedKey)) return ResponseEntity.status(401).build()

        val entity = repoLocationRepository.findById(repoId).orElse(null) ?: return ResponseEntity.notFound().build()

        req.updates.forEach { update ->
            val existing = repoReplicaRepository.findByIdRepoIdAndIdNodeId(repoId, update.nodeId)
            if (existing == null) {
                repoReplicaRepository.save(
                    RepoReplicaEntity(
                        id = RepoReplicaId(repoId = repoId, nodeId = update.nodeId),
                        health = update.health,
                        lagMs = update.lagMs,
                        lagCommits = update.lagCommits,
                        updatedAt = LocalDateTime.now()
                    )
                )
            } else {
                existing.lagMs = update.lagMs
                update.lagCommits?.let { existing.lagCommits = it }
                update.health?.let { existing.health = it }
                existing.updatedAt = LocalDateTime.now()
                repoReplicaRepository.save(existing)
            }
        }

        entity.updatedAt = LocalDateTime.now()
        val saved = repoLocationRepository.save(entity)
        auditRepository.save(
            GitRoutingAuditEntity(
                actor = actor,
                action = "UPDATE_REPLICA_LAG",
                repoId = repoId,
                payload = objectMapper.writeValueAsString(req)
            )
        )
        return ResponseEntity.ok(saved)
    }

    @GetMapping("/repo/{repoId}/replicas")
    fun listRepoReplicas(
        @PathVariable repoId: Long,
        @RequestHeader(value = "X-Git-Admin-Key", required = false) providedKey: String?
    ): ResponseEntity<List<ReplicaStatusResponse>> {
        if (!authorized(providedKey)) return ResponseEntity.status(401).build()
        val replicas = repoReplicaRepository.findByIdRepoId(repoId)
        val response = replicas.map {
            ReplicaStatusResponse(
                repoId = it.id.repoId,
                nodeId = it.id.nodeId,
                health = it.health,
                lagMs = it.lagMs,
                lagCommits = it.lagCommits,
                updatedAt = it.updatedAt
            )
        }
        return ResponseEntity.ok(response)
    }

    @GetMapping("/replicas/summary")
    fun replicasSummary(
        @RequestParam(required = false) repoId: Long?,
        @RequestParam(defaultValue = "200") limit: Int,
        @RequestHeader(value = "X-Git-Admin-Key", required = false) providedKey: String?
    ): ResponseEntity<List<RepoReplicaSummary>> {
        if (!authorized(providedKey)) return ResponseEntity.status(401).build()
        val rows = if (repoId != null) {
            repoReplicaRepository.findByIdRepoId(repoId)
        } else {
            repoReplicaRepository.findAll().take(limit.coerceAtLeast(1))
        }
        val grouped = rows.groupBy { it.id.repoId }
        val result = grouped.map { (rid, list) ->
            val healthy = list.count { it.health == "healthy" }
            val lagMsValues = list.mapNotNull { it.lagMs }
            val lagCommitsValues = list.mapNotNull { it.lagCommits }
            RepoReplicaSummary(
                repoId = rid,
                replicas = list.size,
                healthy = healthy,
                avgLagMs = if (lagMsValues.isEmpty()) null else lagMsValues.average().toLong(),
                maxLagMs = lagMsValues.maxOrNull(),
                avgLagCommits = if (lagCommitsValues.isEmpty()) null else lagCommitsValues.average().toLong(),
                maxLagCommits = lagCommitsValues.maxOrNull()
            )
        }
        return ResponseEntity.ok(result)
    }

    @GetMapping("/replicas/summary-by-node")
    fun replicasSummaryByNode(
        @RequestParam(defaultValue = "200") limit: Int,
        @RequestHeader(value = "X-Git-Admin-Key", required = false) providedKey: String?
    ): ResponseEntity<List<NodeReplicaSummary>> {
        if (!authorized(providedKey)) return ResponseEntity.status(401).build()
        val rows = repoReplicaRepository.findAll().take(limit.coerceAtLeast(1))
        val grouped = rows.groupBy { it.id.nodeId }
        val result = grouped.map { (nodeId, list) ->
            val healthy = list.count { it.health == "healthy" }
            val lagMsValues = list.mapNotNull { it.lagMs }
            val lagCommitsValues = list.mapNotNull { it.lagCommits }
            val node = gitNodeRepository.findById(nodeId).orElse(null)
            NodeReplicaSummary(
                nodeId = nodeId,
                zone = node?.zone,
                region = node?.region,
                replicas = list.size,
                healthy = healthy,
                avgLagMs = if (lagMsValues.isEmpty()) null else lagMsValues.average().toLong(),
                maxLagMs = lagMsValues.maxOrNull(),
                avgLagCommits = if (lagCommitsValues.isEmpty()) null else lagCommitsValues.average().toLong(),
                maxLagCommits = lagCommitsValues.maxOrNull()
            )
        }
        return ResponseEntity.ok(result)
    }

    @GetMapping("/replicas/summary-by-zone")
    fun replicasSummaryByZone(
        @RequestParam(defaultValue = "200") limit: Int,
        @RequestHeader(value = "X-Git-Admin-Key", required = false) providedKey: String?
    ): ResponseEntity<List<ZoneReplicaSummary>> {
        if (!authorized(providedKey)) return ResponseEntity.status(401).build()
        val rows = repoReplicaRepository.findAll().take(limit.coerceAtLeast(1))
        val nodeMap = gitNodeRepository.findAll().associateBy { it.nodeId }
        val grouped = rows.groupBy { nodeMap[it.id.nodeId]?.zone ?: "unknown" }
        val result = grouped.map { (zone, list) ->
            val healthy = list.count { it.health == "healthy" }
            val lagMsValues = list.mapNotNull { it.lagMs }
            val lagCommitsValues = list.mapNotNull { it.lagCommits }
            ZoneReplicaSummary(
                zone = zone,
                replicas = list.size,
                healthy = healthy,
                avgLagMs = if (lagMsValues.isEmpty()) null else lagMsValues.average().toLong(),
                maxLagMs = lagMsValues.maxOrNull(),
                avgLagCommits = if (lagCommitsValues.isEmpty()) null else lagCommitsValues.average().toLong(),
                maxLagCommits = lagCommitsValues.maxOrNull()
            )
        }
        return ResponseEntity.ok(result)
    }

    @PostMapping("/replicas/backfill")
    fun triggerBackfill(
        @RequestHeader(value = "X-Git-Admin-Key", required = false) providedKey: String?
    ): ResponseEntity<Void> {
        if (!authorized(providedKey)) return ResponseEntity.status(401).build()
        backfillJob.runBackfill()
        return ResponseEntity.ok().build()
    }

    @GetMapping("/replicas/backfill-stats")
    fun backfillStats(
        @RequestHeader(value = "X-Git-Admin-Key", required = false) providedKey: String?
    ): ResponseEntity<BackfillStatsResponse> {
        if (!authorized(providedKey)) return ResponseEntity.status(401).build()
        val columnsExist = jdbcTemplate.queryForObject(
            """
            select count(*) from information_schema.columns
            where table_schema = database()
              and table_name = 'repo_location'
              and column_name in ('replica_node_ids', 'replica_health', 'replica_lag_ms', 'replica_lag_commits')
            """.trimIndent(),
            Int::class.java
        ) ?: 0

        val stats = if (columnsExist >= 4) {
            val expected = jdbcTemplate.queryForObject(
                """
                select ifnull(sum(json_length(replica_node_ids)), 0)
                from repo_location
                where replica_node_ids is not null and replica_node_ids <> '[]'
                """.trimIndent(),
                Long::class.java
            ) ?: 0L
            val actual = jdbcTemplate.queryForObject(
                "select count(*) from repo_replica",
                Long::class.java
            ) ?: 0L
            BackfillStatsResponse(
                jsonColumnsPresent = true,
                expectedReplicas = expected,
                actualReplicas = actual,
                delta = actual - expected
            )
        } else {
            val actual = jdbcTemplate.queryForObject(
                "select count(*) from repo_replica",
                Long::class.java
            ) ?: 0L
            BackfillStatsResponse(
                jsonColumnsPresent = false,
                expectedReplicas = null,
                actualReplicas = actual,
                delta = null
            )
        }
        return ResponseEntity.ok(stats)
    }

    @GetMapping("/nodes")
    fun listNodes(
        @RequestHeader(value = "X-Git-Admin-Key", required = false) providedKey: String?
    ): ResponseEntity<List<GitNodeEntity>> {
        if (!authorized(providedKey)) return ResponseEntity.status(401).build()
        return ResponseEntity.ok(gitNodeRepository.findAll())
    }

    @PostMapping("/nodes")
    fun upsertNode(
        @RequestBody req: GitNodeUpsertRequest,
        @RequestHeader(value = "X-Git-Admin-Key", required = false) providedKey: String?,
        @RequestHeader(value = "X-Git-Admin-Actor", required = false) actor: String?
    ): ResponseEntity<GitNodeEntity> {
        if (!authorized(providedKey)) return ResponseEntity.status(401).build()
        val now = LocalDateTime.now()
        val existing = gitNodeRepository.findById(req.nodeId).orElse(null)
        val entity = if (existing == null) {
            GitNodeEntity(
                nodeId = req.nodeId,
                host = req.host,
                zone = req.zone,
                region = req.region,
                status = req.status ?: "healthy",
                diskUsagePct = req.diskUsagePct,
                iops = req.iops,
                repoCount = req.repoCount,
                lastHeartbeatAt = now
            )
        } else {
            existing.host = req.host
            existing.zone = req.zone
            existing.region = req.region
            req.status?.let { existing.status = it }
            existing.diskUsagePct = req.diskUsagePct
            existing.iops = req.iops
            existing.repoCount = req.repoCount
            existing.lastHeartbeatAt = now
            existing
        }

        val saved = gitNodeRepository.save(entity)
        auditRepository.save(
            GitRoutingAuditEntity(
                actor = actor,
                action = "UPSERT_NODE",
                nodeId = req.nodeId,
                payload = objectMapper.writeValueAsString(req)
            )
        )
        return ResponseEntity.ok(saved)
    }

    private fun authorized(provided: String?): Boolean =
        adminKey.isBlank() || adminKey == provided

    private fun syncReplicas(repoId: Long, req: RepoLocationUpsertRequest) {
        val replicaIds = req.replicaNodeIds ?: emptyList()
        val healthMap = req.replicaHealth ?: emptyMap()
        val lagMap = req.replicaLagMs ?: emptyMap()
        val lagCommitMap = req.replicaLagCommits ?: emptyMap()

        val existing = repoReplicaRepository.findByIdRepoId(repoId).associateBy { it.id.nodeId }
        val now = LocalDateTime.now()

        replicaIds.forEach { nodeId ->
            val entity = existing[nodeId]
            if (entity == null) {
                repoReplicaRepository.save(
                    RepoReplicaEntity(
                        id = RepoReplicaId(repoId = repoId, nodeId = nodeId),
                        health = healthMap[nodeId],
                        lagMs = lagMap[nodeId],
                        lagCommits = lagCommitMap[nodeId],
                        updatedAt = now
                    )
                )
            } else {
                entity.health = healthMap[nodeId] ?: entity.health
                entity.lagMs = lagMap[nodeId] ?: entity.lagMs
                entity.lagCommits = lagCommitMap[nodeId] ?: entity.lagCommits
                entity.updatedAt = now
                repoReplicaRepository.save(entity)
            }
        }

        existing.keys.filterNot { replicaIds.contains(it) }.forEach { nodeId ->
            repoReplicaRepository.deleteByIdRepoIdAndIdNodeId(repoId, nodeId)
        }
    }
}

data class RepoLocationUpsertRequest(
    val primaryNodeId: String,
    val replicaNodeIds: List<String>? = null,
    val replicaHealth: Map<String, String>? = null,
    val replicaLagMs: Map<String, Long>? = null,
    val replicaLagCommits: Map<String, Long>? = null,
    val lastWriteCommit: String? = null
)

data class ReplicaLagUpdateRequest(
    val updates: List<ReplicaLagUpdate>
)

data class ReplicaLagUpdate(
    val nodeId: String,
    val lagMs: Long,
    val lagCommits: Long? = null,
    val health: String? = null
)

data class GitNodeUpsertRequest(
    val nodeId: String,
    val host: String,
    val zone: String? = null,
    val region: String? = null,
    val status: String? = null,
    val diskUsagePct: java.math.BigDecimal? = null,
    val iops: Int? = null,
    val repoCount: Int? = null
)

data class ReplicaStatusResponse(
    val repoId: Long,
    val nodeId: String,
    val health: String?,
    val lagMs: Long?,
    val lagCommits: Long?,
    val updatedAt: java.time.LocalDateTime?
)

data class RepoReplicaSummary(
    val repoId: Long,
    val replicas: Int,
    val healthy: Int,
    val avgLagMs: Long?,
    val maxLagMs: Long?,
    val avgLagCommits: Long?,
    val maxLagCommits: Long?
)

data class NodeReplicaSummary(
    val nodeId: String,
    val zone: String?,
    val region: String?,
    val replicas: Int,
    val healthy: Int,
    val avgLagMs: Long?,
    val maxLagMs: Long?,
    val avgLagCommits: Long?,
    val maxLagCommits: Long?
)

data class ZoneReplicaSummary(
    val zone: String,
    val replicas: Int,
    val healthy: Int,
    val avgLagMs: Long?,
    val maxLagMs: Long?,
    val avgLagCommits: Long?,
    val maxLagCommits: Long?
)

data class BackfillStatsResponse(
    val jsonColumnsPresent: Boolean,
    val expectedReplicas: Long?,
    val actualReplicas: Long,
    val delta: Long?
)
