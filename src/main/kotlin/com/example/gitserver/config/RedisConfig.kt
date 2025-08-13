package com.example.gitserver.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

@Configuration
@EnableCaching
class RedisConfig {

    @Bean
    fun redisConnectionFactory(): RedisConnectionFactory =
        LettuceConnectionFactory()

    @Bean
    @Primary
    fun redisObjectMapper(): ObjectMapper =
        JsonMapper.builder()
            .addModule(KotlinModule.Builder().build())
            .addModule(JavaTimeModule())
            .build()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    @Bean
    fun redisValueSerializer(objectMapper: ObjectMapper): GenericJackson2JsonRedisSerializer =
        GenericJackson2JsonRedisSerializer(objectMapper)

    @Bean
    fun redisTemplate(
        cf: RedisConnectionFactory,
        ser: GenericJackson2JsonRedisSerializer
    ): RedisTemplate<String, Any> {
        val keySer = StringRedisSerializer()
        return RedisTemplate<String, Any>().apply {
            connectionFactory = cf
            keySerializer = keySer
            valueSerializer = ser
            hashKeySerializer = keySer
            hashValueSerializer = ser
            afterPropertiesSet()
        }
    }

    @Bean
    @Primary
    fun cacheManager(
        cf: RedisConnectionFactory,
        ser: GenericJackson2JsonRedisSerializer
    ): CacheManager {
        val keySer = StringRedisSerializer()
        val defaults = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(keySer))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(ser))
        val perCache = mapOf(
            "commitByHash" to defaults.entryTtl(Duration.ofHours(6)),
            "treeAtRoot" to defaults.entryTtl(Duration.ofHours(2)),
            "treeByPath" to defaults.entryTtl(Duration.ofHours(2)),
            "blobMeta" to defaults.entryTtl(Duration.ofHours(12)),
            "commitRowByHash" to defaults.entryTtl(Duration.ofHours(6)),
        )
        return RedisCacheManager.builder(cf)
            .cacheDefaults(defaults)
            .withInitialCacheConfigurations(perCache)
            .build()
    }
}
