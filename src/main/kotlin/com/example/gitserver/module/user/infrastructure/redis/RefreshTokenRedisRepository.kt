package com.example.gitserver.module.user.infrastructure.redis


import com.example.gitserver.module.user.application.command.RefreshTokenCommand
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
) : RefreshTokenCommand, RefreshTokenQuery {

    /**
     * Redis에 RefreshToken을 저장합니다.
     *
     * @param token 저장할 RefreshToken
     */
    override fun save(token: RefreshToken) {
        val ttl = Duration.between(Instant.now(), token.expiredAt)
        redisTemplate.opsForValue().set(
            "refresh_token:${token.userId}",
            token.value,
            ttl
        )
        logger.debug("Token size: ${token.value.length} bytes")
    }

    /**
     * Redis에서 userId에 해당하는 RefreshToken을 조회합니다.
     *
     * @param userId 조회할 사용자 ID
     * @return RefreshToken 객체 또는 null
     */
    override fun findByUserId(userId: Long): RefreshToken? {
        val value = redisTemplate.opsForValue().get("refresh_token:$userId")
        return value?.let {
            RefreshToken(userId, it, Instant.now().plusSeconds(60 * 60 * 24 * 14))
        }
    }

    /**
     * Redis에서 userId에 해당하는 RefreshToken을 삭제합니다.
     *
     * @param userId 삭제할 사용자 ID
     */
    override fun delete(userId: Long) {
        redisTemplate.delete("refresh_token:$userId")
    }
}