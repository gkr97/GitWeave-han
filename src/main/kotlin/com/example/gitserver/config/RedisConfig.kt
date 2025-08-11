package com.example.gitserver.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
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
    fun redisConnectionFactory(): RedisConnectionFactory = LettuceConnectionFactory()

    @Bean
    fun redisObjectMapper(): ObjectMapper =
        ObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    @Bean
    fun redisTemplate(cf: RedisConnectionFactory, om: ObjectMapper): RedisTemplate<String, Any> {
        val template = RedisTemplate<String, Any>()
        template.connectionFactory = cf
        template.keySerializer = StringRedisSerializer()
        val valueSer = GenericJackson2JsonRedisSerializer(om)
        template.valueSerializer = valueSer
        template.hashKeySerializer = StringRedisSerializer()
        template.hashValueSerializer = valueSer
        template.afterPropertiesSet()
        return template
    }

    @Bean
    fun cacheManager(cf: LettuceConnectionFactory, om: ObjectMapper): CacheManager {
        val keySer = StringRedisSerializer()
        val valSer = GenericJackson2JsonRedisSerializer(om)

        val defaults = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(keySer))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valSer))

        val perCache = mapOf(
            "commitByHash" to defaults.entryTtl(Duration.ofHours(6)),
            "treeAtRoot"   to defaults.entryTtl(Duration.ofHours(2)),
            "treeByPath"   to defaults.entryTtl(Duration.ofHours(2)),
            "blobMeta"     to defaults.entryTtl(Duration.ofHours(12))
        )

        return RedisCacheManager.builder(cf)
            .cacheDefaults(defaults)
            .withInitialCacheConfigurations(perCache)
            .build()
    }
}