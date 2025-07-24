package com.example.gitserver.module.user.application.service

import com.example.gitserver.common.jwt.JwtProvider
import com.example.gitserver.module.user.application.command.RefreshTokenCommand
import com.example.gitserver.module.user.application.query.RefreshTokenQuery
import com.example.gitserver.module.user.domain.vo.RefreshToken
import com.example.gitserver.module.user.exception.AuthException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

@Service
class AuthQueryService(
    private val refreshTokenQuery: RefreshTokenQuery,
    private val refreshTokenCommand: RefreshTokenCommand,
    private val jwtProvider: JwtProvider
) {
    /**
     * @param userId    토큰에 들어있는 userId
     * @param email     토큰에 들어있는 email
     * @param oldRefreshToken 클라이언트가 보내준 기존 refreshToken
     * @return Pair<새 AccessToken, 새 RefreshToken>
     */
    @Transactional
    fun refresh(userId: Long, email: String, oldRefreshToken: String): Pair<String, String> {
        val savedToken = refreshTokenQuery.findByUserId(userId)
            ?: throw AuthException(
                code = "REFRESH_TOKEN_NOT_FOUND",
                message = "리프레시 토큰이 존재하지 않습니다.",
                status = HttpStatus.UNAUTHORIZED
            )
        if (savedToken.value != oldRefreshToken) {
            throw AuthException(
                code = "REFRESH_TOKEN_MISMATCH",
                message = "리프레시 토큰이 일치하지 않습니다.",
                status = HttpStatus.UNAUTHORIZED
            )
        }

        val newAccessToken = jwtProvider.generateToken(userId, email)
        val newRefreshTokenValue = UUID.randomUUID().toString()
        val expiredAt = Instant.now().plusSeconds(14 * 24 * 60 * 60)
        refreshTokenCommand.save(RefreshToken(userId, newRefreshTokenValue, expiredAt))
        return Pair(newAccessToken, newRefreshTokenValue)
    }
}
