package com.example.gitserver.module.user.application.service

import io.jsonwebtoken.Jwts
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class JwtService(
    @Value("\${jwt.secret}")
    private val secretKey: String
) {

    /**
     * Bearer 토큰에서 사용자 ID를 추출합니다.
     *
     * @param authorization Bearer 토큰 문자열
     * @return 사용자 ID 또는 null
     */
    fun resolveUserIdByBearer(authorization: String?): Long? {
        val token = authorization?.takeIf { it.startsWith("Bearer ") }?.substring(7) ?: return null
        return parseUserId(token)
    }

    /**
     * JWT 토큰에서 사용자 ID를 추출합니다.
     *
     * @param token JWT 토큰 문자열
     * @return 사용자 ID 또는 null
     */
    private fun parseUserId(token: String): Long? {
        return try {
            val claims = Jwts.parser()
                .setSigningKey(secretKey.toByteArray())
                .parseClaimsJws(token)
                .body
            claims["userId"]?.toString()?.toLongOrNull()
        } catch (e: Exception) {
            null
        }
    }
}
