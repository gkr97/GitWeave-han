package com.example.gitserver.module.repository.infrastructure.job

import com.example.gitserver.module.repository.infrastructure.redis.RedisCountService
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional


@Component
class RepositoryStarReconciliationJob(
    private val jdbcTemplate: JdbcTemplate,
    private val redisCountService: RedisCountService
) {
    private val log = LoggerFactory.getLogger(RepositoryStarReconciliationJob::class.java)

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    fun reconcileStars() {
        log.info("Starting repository star reconciliation job")
        val sql = """
            SELECT repository_id, COUNT(*) AS cnt
            FROM repository_star
            GROUP BY repository_id
        """.trimIndent()

        val rows = jdbcTemplate.queryForList(sql)
        var updated = 0
        for (row in rows) {
            val repoId = (row["repository_id"] as Number).toLong()
            val cntLong = (row["cnt"] as Number).toLong()
            val cnt = if (cntLong > Int.MAX_VALUE) Int.MAX_VALUE else cntLong.toInt()

            try {
                jdbcTemplate.update(
                    """
                    INSERT INTO repository_stats (repository_id, stars)
                    VALUES (?, ?)
                    ON DUPLICATE KEY UPDATE stars = ?
                    """.trimIndent(),
                    repoId, cnt, cnt
                )
            } catch (ex: Exception) {
                log.warn("Failed to update repository_stats for repo=$repoId", ex)
                continue
            }

            try {
                redisCountService.setRepoStars(repoId, cnt)
            } catch (ex: Exception) {
                log.warn("Failed to set redis count for repo=$repoId", ex)
            }
            updated++
        }

        log.info("Reconciliation finished, updated entries=$updated")
    }
}
