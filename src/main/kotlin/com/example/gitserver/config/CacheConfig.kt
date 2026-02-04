package com.example.gitserver.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.caffeine.CaffeineCache
import org.springframework.cache.support.SimpleCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

/**
 * Caffeine Cache 설정
 */
@Configuration
class CacheConfig {

    @Bean(name = ["caffeineCacheManager"])
    fun caffeineCacheManager(): CacheManager {
        val caches = listOf(
            CaffeineCache(
                "repoDetail",
                Caffeine.newBuilder()
                    .maximumSize(2_000)
                    .expireAfterWrite(Duration.ofSeconds(60))
                    .build()
            ),
            CaffeineCache(
                "repoBranches",
                Caffeine.newBuilder()
                    .maximumSize(5_000)
                    .expireAfterWrite(Duration.ofSeconds(60))
                    .build()
            ),
            CaffeineCache(
                "myRepos",
                Caffeine.newBuilder()
                    .maximumSize(5_000)
                    .expireAfterWrite(Duration.ofSeconds(30))
                    .build()
            ),
            CaffeineCache(
                "userRepos",
                Caffeine.newBuilder()
                    .maximumSize(5_000)
                    .expireAfterWrite(Duration.ofSeconds(30))
                    .build()
            ),
            CaffeineCache(
                "commonCodes",
                Caffeine.newBuilder()
                    .maximumSize(1_000)
                    .expireAfterWrite(Duration.ofMinutes(10))
                    .build()
            ),
            CaffeineCache(
                "latestCommit",
                Caffeine.newBuilder()
                    .maximumSize(20_000)
                    .expireAfterWrite(Duration.ofSeconds(30))
                    .build()
            ),
            CaffeineCache(
                "fileTree",
                Caffeine.newBuilder()
                    .maximumSize(20_000)
                    .expireAfterWrite(Duration.ofSeconds(60))
                    .build()
            ),
            CaffeineCache(
                "commitInfo",
                Caffeine.newBuilder()
                    .maximumSize(100_000)
                    .expireAfterWrite(Duration.ofHours(1))
                    .build()
            ),
            CaffeineCache(
                "userByIdShort",
                Caffeine.newBuilder()
                    .maximumSize(100_000)
                    .expireAfterWrite(Duration.ofSeconds(60))
                    .build()
            ),
            CaffeineCache(
                "userDetailsByIdShort",
                Caffeine.newBuilder()
                    .maximumSize(100_000)
                    .expireAfterWrite(Duration.ofSeconds(60))
                    .build()
            )
        )

        return SimpleCacheManager().apply { setCaches(caches) }
    }
}
