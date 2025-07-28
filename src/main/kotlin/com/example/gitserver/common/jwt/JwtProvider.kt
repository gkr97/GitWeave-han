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

    /**
     * JWT 토큰을 생성합니다.
     *
     * @param userId 사용자 ID
     * @param email 사용자 이메일
     * @return 생성된 JWT 토큰 문자열
     */
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

    /**
     * JWT 토큰의 유효성을 검증합니다.
     *
     * @param token JWT 토큰 문자열
     * @return 유효한 경우 true, 그렇지 않은 경우 false
     */
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

    /**
     * JWT 토큰에서 사용자 ID를 추출합니다.
     *
     * @param token JWT 토큰 문자열
     * @return 사용자 ID
     */
    fun getUserId(token: String): Long {
        return getClaims(token).subject.toLong()
    }

    /**
     * JWT 토큰에서 이메일을 추출합니다.
     *
     * @param token JWT 토큰 문자열
     * @return 사용자 이메일
     */
    fun getEmail(token: String): String {
        return getClaims(token).get("email", String::class.java)
    }

    /**
     * HTTP 요청에서 JWT 토큰을 추출합니다.
     *
     * @param request HTTP 요청 객체
     * @return JWT 토큰 문자열
     * @throws UnauthorizedException JWT 토큰이 없거나 잘못된 경우
     */
    fun resolveToken(request: HttpServletRequest): String {
        val bearer = request.getHeader("Authorization")
        return if (bearer != null && bearer.startsWith("Bearer ")) {
            bearer.substring(7)
        } else {
            throw UnauthorizedException("UNAUTHORIZED","JWT 토큰이 필요합니다.")
        }
    }

    /**
     * JWT 토큰에서 Claims 객체를 추출합니다.
     *
     * @param token JWT 토큰 문자열
     * @return Claims 객체
     * @throws TokenExpiredException 토큰이 만료된 경우
     * @throws InvalidTokenException 토큰이 잘못된 경우
     */
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
