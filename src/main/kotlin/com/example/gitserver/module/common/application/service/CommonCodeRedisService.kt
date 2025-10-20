package com.example.gitserver.module.common.application.service

import com.example.gitserver.module.common.dto.CommonCodeDetailResponse
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service

/**
 * 공통 코드 정보를 Redis에 저장하고 조회하는 서비스
 */
@Service
class CommonCodeRedisService(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    private val keyPrefix = "common_code:"

    fun getDetailsByCode(code: String): List<CommonCodeDetailResponse>? {
        val json = redisTemplate.opsForValue().get(keyPrefix + code) ?: return null
        return objectMapper.readValue(json, object : TypeReference<List<CommonCodeDetailResponse>>() {})
    }

    fun saveDetailsByCode(code: String, details: List<CommonCodeDetailResponse>) {
        val json = objectMapper.writeValueAsString(details)
        redisTemplate.opsForValue().set(keyPrefix + code, json)
    }
}