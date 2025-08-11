package com.example.gitserver.module.gitindex.infrastructure.redis

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ScanOptions
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class GitIndexEvictor(
    private val redis: StringRedisTemplate,
) {
    private val log = LoggerFactory.getLogger(GitIndexEvictor::class.java)

    /**
     * 특정 레포지토리의 모든 캐시를 제거합니다.
     * - treeAtRoot: 루트 트리 캐시
     * - treeByPath: 특정 경로 트리 아이템 캐시
     * - commitByHash: 커밋 메타 캐시
     * - commitByHash (로우): 커밋 로우 캐시
     * - blobMeta: 블롭 메타 캐시
     */
    fun evictAllOfRepository(repositoryId: Long) {
        deleteByPrefix("treeAtRoot", "t:$repositoryId:")
        deleteByPrefix("treeByPath", "tp:$repositoryId:")
        deleteByPrefix("commitByHash", "c:$repositoryId:")
        deleteByPrefix("commitByHash", "cr:$repositoryId:")
        deleteByPrefix("blobMeta", "b:$repositoryId:")
    }

    private fun deleteByPrefix(cacheName: String, keyPrefix: String) {
        val pattern = "$cacheName::$keyPrefix*"
        val scan = ScanOptions.scanOptions().match(pattern).count(1000).build()
        val conn = redis.connectionFactory?.connection ?: return
        conn.use { c ->
            c.scan(scan).use { cursor ->
                var cnt = 0L
                cursor.forEach { key ->
                    c.keyCommands().del(key)
                    cnt++
                }
                log.info("Redis evict done cache={} prefix={} deleted={}", cacheName, keyPrefix, cnt)
            }
        }
    }
}
