package com.example.gitserver.common.util

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.serializer.RedisSerializer

/**
 * Jackson을 사용하여 List<T> 타입을 Redis에 직렬화/역직렬화하는 RedisSerializer 구현체
 */
class JacksonListRedisSerializer<T>(
    private val objectMapper: ObjectMapper,
    private val typeReference: TypeReference<List<T>>
) : RedisSerializer<List<T>> {

    override fun serialize(value: List<T>?): ByteArray? =
        if (value == null) null else objectMapper.writeValueAsBytes(value)

    override fun deserialize(bytes: ByteArray?): List<T>? =
        if (bytes == null) null else objectMapper.readValue(bytes, typeReference)
}