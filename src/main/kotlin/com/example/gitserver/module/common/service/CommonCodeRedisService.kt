package com.example.gitserver.module.common.service

import com.example.gitserver.module.common.dto.CommonCodeDetailResponse
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional


@Service
class CommonCodeRedisService(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    private val keyPrefix = "common_code:"

    @Transactional
    fun getDetailsByCode(code: String): List<CommonCodeDetailResponse>? {
        val json = redisTemplate.opsForValue().get(keyPrefix + code) ?: return null
        return objectMapper.readValue(json, object : TypeReference<List<CommonCodeDetailResponse>>() {})
    }

    @Transactional
    fun saveDetailsByCode(code: String, details: List<CommonCodeDetailResponse>) {
        val json = objectMapper.writeValueAsString(details)
        redisTemplate.opsForValue().set(keyPrefix + code, json)
    }
}