package com.example.gitserver.config

import com.example.gitserver.common.util.JacksonListRedisSerializer
import com.example.gitserver.module.gitindex.domain.dto.FileMeta
import com.example.gitserver.module.gitindex.domain.dto.TreeItem
import com.example.gitserver.module.repository.interfaces.dto.CommitResponse
import com.example.gitserver.module.repository.interfaces.dto.TreeNodeResponse
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.cache.CacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

/**
 * Redis 설정
 */
@Configuration
class RedisConfig {

    @Bean
    fun redisConnectionFactory(): RedisConnectionFactory =
        LettuceConnectionFactory()

    @Bean
    @Primary
    fun webObjectMapper(): ObjectMapper =
        JsonMapper.builder()
            .addModule(KotlinModule.Builder().build())
            .addModule(JavaTimeModule())
            .build()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    @Bean(name = ["redisObjectMapper"])
    fun redisObjectMapper(): ObjectMapper =
        JsonMapper.builder()
            .addModule(KotlinModule.Builder().build())
            .addModule(JavaTimeModule())
            .build()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
            )

    @Bean
    fun redisTemplate(
        cf: RedisConnectionFactory,
        redisObjectMapper: ObjectMapper
    ): RedisTemplate<String, Any> {
        val keySer = StringRedisSerializer()
        val valueSer = Jackson2JsonRedisSerializer(redisObjectMapper, Any::class.java)
        return RedisTemplate<String, Any>().apply {
            connectionFactory = cf
            keySerializer = keySer
            valueSerializer = valueSer
            hashKeySerializer = keySer
            hashValueSerializer = valueSer
            afterPropertiesSet()
        }
    }

    @Bean(name = ["redisCacheManager"])
    fun redisCacheManager(
        cf: RedisConnectionFactory,
        redisObjectMapper: ObjectMapper
    ): CacheManager {
        val keySer = StringRedisSerializer()

        val commitSerializer =
            Jackson2JsonRedisSerializer(redisObjectMapper, CommitResponse::class.java)
        val treeNodeListSerializer =
            JacksonListRedisSerializer(redisObjectMapper, object : TypeReference<List<TreeNodeResponse>>() {})
        val treeItemSerializer =
            Jackson2JsonRedisSerializer(redisObjectMapper, TreeItem::class.java)
        val blobMetaSerializer =
            Jackson2JsonRedisSerializer(redisObjectMapper, FileMeta::class.java)

        val defaults = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(keySer))

        val perCache = mapOf(
            "commitByHash" to defaults.entryTtl(Duration.ofHours(6))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(commitSerializer)),
            "treeAtRoot" to defaults.entryTtl(Duration.ofHours(2))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(treeNodeListSerializer)),
            "treeByPath" to defaults.entryTtl(Duration.ofHours(2))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(treeItemSerializer)),
            "blobMeta" to defaults.entryTtl(Duration.ofHours(12))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(blobMetaSerializer)),
            "commitRowByHash" to defaults.entryTtl(Duration.ofHours(6))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(commitSerializer)),
        )

        return RedisCacheManager.builder(cf)
            .cacheDefaults(defaults)
            .withInitialCacheConfigurations(perCache)
            .build()
    }
}
