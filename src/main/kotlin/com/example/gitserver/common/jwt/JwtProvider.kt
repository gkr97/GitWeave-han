package com.example.gitserver.common.jwt

import com.example.gitserver.module.user.exception.InvalidTokenException
import com.example.gitserver.module.user.exception.TokenExpiredException
import com.example.gitserver.module.user.exception.UnauthorizedException
import io.jsonwebtoken.*
import io.jsonwebtoken.security.Keys
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*
import javax.crypto.SecretKey

@Component
class JwtProvider(
    @Value("\${jwt.secret}") private val secret: String,
    @Value("\${jwt.expirationMillis}") private val expirationMillis: Long,
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray())


    fun generateToken(userId: Long, email: String): String {
        val now = Date()
        val expiry = Date(now.time + expirationMillis)
        return Jwts.builder()
            .setSubject(userId.toString())
            .claim("email", email)
            .setIssuedAt(now)
            .setExpiration(expiry)
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()
    }

    fun validateToken(token: String): Boolean {
        return try {
            val claims = getClaims(token)
            !claims.expiration.before(Date())
        } catch (e: JwtException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    fun getUserId(token: String): Long {
        return getClaims(token).subject.toLong()
    }

    fun getEmail(token: String): String {
        return getClaims(token).get("email", String::class.java)
    }

    fun resolveToken(request: HttpServletRequest): String {
        val bearer = request.getHeader("Authorization")
        return if (bearer != null && bearer.startsWith("Bearer ")) {
            bearer.substring(7)
        } else {
            throw UnauthorizedException("UNAUTHORIZED","JWT 토큰이 필요합니다.")
        }
    }

    private fun getClaims(token: String): Claims {
        try {
            return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .body
        } catch (e: ExpiredJwtException) {
            throw TokenExpiredException()
        } catch (e: UnsupportedJwtException) {
            throw InvalidTokenException("지원하지 않는 JWT 토큰입니다.")
        } catch (e: MalformedJwtException) {
            throw InvalidTokenException("잘못된 JWT 토큰입니다.")
        } catch (e: SecurityException) {
            throw InvalidTokenException("JWT 서명 검증 실패.")
        } catch (e: IllegalArgumentException) {
            throw InvalidTokenException("JWT 토큰이 비었습니다.")
        }
    }

}
