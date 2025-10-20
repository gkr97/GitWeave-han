package com.example.gitserver.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.support.CompositeCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

/**
 * Composite Cache 설정 (Caffeine + Redis)
 */
@Configuration
@EnableCaching
class CompositeCacheConfig {

    @Bean(name = ["cacheManager"])
    @Primary
    fun cacheManager(
        @Qualifier("caffeineCacheManager") caffeine: CacheManager,
        @Qualifier("redisCacheManager") redis: CacheManager
    ): CacheManager =
        CompositeCacheManager(caffeine, redis).apply {
            setFallbackToNoOpCache(false)
        }
}
