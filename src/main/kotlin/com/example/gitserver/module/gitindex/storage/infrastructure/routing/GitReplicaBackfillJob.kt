package com.example.gitserver.module.gitindex.storage.infrastructure.routing

import com.example.gitserver.module.gitindex.storage.domain.RepoReplicaEntity
import com.example.gitserver.module.gitindex.storage.domain.RepoReplicaId
import com.example.gitserver.module.gitindex.storage.infrastructure.persistence.RepoReplicaRepository
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean
import org.springframework.context.annotation.Profile

@Component
@Profile("gitstorage")
class GitReplicaBackfillJob(
    private val jdbcTemplate: JdbcTemplate,
    private val repoReplicaRepository: RepoReplicaRepository,
    private val objectMapper: ObjectMapper,
    @Value("\${git.routing.backfill.enabled:false}") private val enabled: Boolean,
    @Value("\${git.routing.backfill.run-once:true}") private val runOnce: Boolean
) {
    private val log = KotlinLogging.logger {}
    private val ranOnce = AtomicBoolean(false)

    @Scheduled(fixedDelayString = "\${git.routing.backfill.interval-ms:60000}")
    fun scheduled() {
        if (!enabled) return
        if (runOnce && ranOnce.get()) return
        runBackfill()
        if (runOnce) ranOnce.set(true)
    }

    fun runBackfill() {
        if (!replicaColumnsExist()) {
            log.info { "[ReplicaBackfill] repo_location json columns not found. skip." }
            return
        }

        val rows = jdbcTemplate.queryForList(
            """
            select repo_id, replica_node_ids, replica_health, replica_lag_ms, replica_lag_commits
            from repo_location
            where replica_node_ids is not null and replica_node_ids <> '[]'
            """.trimIndent()
        )

        var created = 0
        rows.forEach { row ->
            val repoId = (row["repo_id"] as Number).toLong()
            val replicaIdsJson = row["replica_node_ids"]?.toString()
            if (replicaIdsJson.isNullOrBlank()) return@forEach

            val replicaIds = parseList(replicaIdsJson)
            if (replicaIds.isEmpty()) return@forEach

            val healthMap = parseStringMap(row["replica_health"]?.toString())
            val lagMap = parseLongMap(row["replica_lag_ms"]?.toString())
            val lagCommitMap = parseLongMap(row["replica_lag_commits"]?.toString())

            val entities = replicaIds.map { nodeId ->
                RepoReplicaEntity(
                    id = RepoReplicaId(repoId = repoId, nodeId = nodeId),
                    health = healthMap[nodeId],
                    lagMs = lagMap[nodeId],
                    lagCommits = lagCommitMap[nodeId]
                )
            }
            repoReplicaRepository.saveAll(entities)
            created += entities.size
        }

        log.info { "[ReplicaBackfill] completed rows=$created" }
    }

    private fun replicaColumnsExist(): Boolean {
        val count = jdbcTemplate.queryForObject(
            """
            select count(*) from information_schema.columns
            where table_schema = database()
              and table_name = 'repo_location'
              and column_name in ('replica_node_ids', 'replica_health', 'replica_lag_ms', 'replica_lag_commits')
            """.trimIndent(),
            Int::class.java
        ) ?: 0
        return count >= 4
    }

    private fun parseList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            objectMapper.readValue(json, object : TypeReference<List<String>>() {})
        }.getOrDefault(emptyList())
    }

    private fun parseStringMap(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            objectMapper.readValue(json, object : TypeReference<Map<String, String>>() {})
        }.getOrDefault(emptyMap())
    }

    private fun parseLongMap(json: String?): Map<String, Long> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            objectMapper.readValue(json, object : TypeReference<Map<String, Long>>() {})
        }.getOrDefault(emptyMap())
    }
}
