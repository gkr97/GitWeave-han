package com.example.gitserver.config

import mu.KotlinLogging
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.cache.CacheManager
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class CacheCleanerConfig(private val cacheManager: CacheManager) {

    private val log = KotlinLogging.logger {}

    /**
     * 애플리케이션 시작 후 오래된 캐시를 정리합니다.
     * 특정 캐시 이름 목록에 대해 캐시를 비웁니다.
     */
    @EventListener(ApplicationReadyEvent::class)
    fun clearOldCaches() {
        val cachesToClear = listOf(
            "commitByHash",
             "treeAtRoot",
             "treeByPath",
             "blobMeta",
             "commitRowByHash",
        )

        cachesToClear.forEach { name ->
            cacheManager.getCache(name)?.also {
                it.clear()
                log.info { "[CacheCleaner] Cleared cache: $name" }
            } ?: log.info { "[CacheCleaner] Cache not found (skipped): $name" }
        }
    }
}
