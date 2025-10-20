package com.example.gitserver.module.user.infrastructure.redis

import com.example.gitserver.module.user.application.command.service.RefreshTokenService
import com.example.gitserver.module.user.application.query.RefreshTokenQuery
import com.example.gitserver.module.user.domain.vo.RefreshToken
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration
import java.time.Instant

private val logger = mu.KotlinLogging.logger {}

@Repository
class RefreshTokenRedisRepository(
    private val redisTemplate: StringRedisTemplate,
) : RefreshTokenService, RefreshTokenQuery {

    /**
     * Redis에 RefreshToken을 저장합니다.
     *
     * @param token 저장할 RefreshToken
     */
    override fun save(token: RefreshToken) {
        val keyUser = "refresh_token:${token.userId}"
        val ttl = Duration.between(Instant.now(), token.expiredAt)

        val prev = redisTemplate.opsForValue().get(keyUser)
        if (prev != null) {
            redisTemplate.delete("refresh_token_value:$prev")
        }

        redisTemplate.opsForValue().set(keyUser, token.value, ttl)
        redisTemplate.opsForValue().set("refresh_token_value:${token.value}", token.userId.toString(), ttl)

        logger.debug("Token size: ${token.value.length} bytes")
    }

    /**
     * Redis에서 userId에 해당하는 RefreshToken을 조회합니다.
     *
     * @param userId 조회할 사용자 ID
     * @return RefreshToken 객체 또는 null
     */
    override fun findByUserId(userId: Long): RefreshToken? {
        val keyUser = "refresh_token:$userId"
        val value = redisTemplate.opsForValue().get(keyUser) ?: return null
        val ttlSec = redisTemplate.getExpire(keyUser) ?: -2
        val expiredAt = if (ttlSec > 0) Instant.now().plusSeconds(ttlSec) else Instant.now()
        return RefreshToken(userId, value, expiredAt)
    }

    /**
     * Redis에서 userId에 해당하는 RefreshToken을 삭제합니다.
     *
     * @param userId 삭제할 사용자 ID
     */
    override fun delete(userId: Long) {
        val keyUser = "refresh_token:$userId"
        val prev = redisTemplate.opsForValue().get(keyUser)
        if (prev != null) {
            redisTemplate.delete("refresh_token_value:$prev")
        }
        redisTemplate.delete(keyUser)
    }

    /**
     * Redis에서 value에 해당하는 RefreshToken을 조회합니다.
     *
     * @param value 조회할 RefreshToken 값
     * @return RefreshToken 객체 또는 null
     */
    override fun findByValue(value: String): RefreshToken? {
        val keyVal = "refresh_token_value:$value"
        val userIdStr = redisTemplate.opsForValue().get(keyVal) ?: return null
        val userId = userIdStr.toLongOrNull() ?: return null

        val keyUser = "refresh_token:$userId"
        val currentValue = redisTemplate.opsForValue().get(keyUser) ?: return null
        if (currentValue != value) return null

        val ttlSec = redisTemplate.getExpire(keyUser) ?: -2
        val expiredAt = if (ttlSec > 0) Instant.now().plusSeconds(ttlSec) else Instant.now()
        return RefreshToken(userId, value, expiredAt)
    }
}
