package com.example.gitserver.module.user.application.query

import com.example.gitserver.common.jwt.JwtProvider
import com.example.gitserver.module.user.application.command.service.RefreshTokenService
import com.example.gitserver.module.user.domain.User
import com.example.gitserver.module.user.domain.vo.RefreshToken
import com.example.gitserver.module.user.exception.AuthException
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

@Service
class AuthQueryService(
    private val refreshTokenQuery: RefreshTokenQuery,
    private val refreshTokenService: RefreshTokenService,
    private val jwtProvider: JwtProvider,
    private val userRepository: UserRepository
) {
    /**
     * @param userId    토큰에 들어있는 userId
     * @param email     토큰에 들어있는 email
     * @param oldRefreshToken 클라이언트가 보내준 기존 refreshToken
     * @return Pair<새 AccessToken, 새 RefreshToken>
     */
    @Transactional
    fun refresh(userId: Long, email: String, oldRefreshToken: String): Pair<String, String> =
        refreshByRefreshToken(oldRefreshToken)

    @Transactional
    fun refreshByRefreshToken(refreshToken: String): Pair<String, String> {
        val saved = refreshTokenQuery.findByValue(refreshToken)
            ?: throw AuthException(
                code = "REFRESH_TOKEN_NOT_FOUND",
                message = "리프레시 토큰이 존재하지 않습니다.",
                status = HttpStatus.UNAUTHORIZED
            )

        if (saved.expiredAt.isBefore(Instant.now())) {
            throw AuthException(
                code = "REFRESH_TOKEN_EXPIRED",
                message = "리프레시 토큰이 만료되었습니다.",
                status = HttpStatus.UNAUTHORIZED
            )
        }

        val user = userRepository.findByIdAndIsDeletedFalse(saved.userId)
            ?: throw AuthException(
                code = "USER_NOT_FOUND",
                message = "사용자를 찾을 수 없습니다.",
                status = HttpStatus.UNAUTHORIZED
            )

        val newAccessToken = jwtProvider.generateToken(user.id, user.email)
        val newRefreshTokenValue = UUID.randomUUID().toString()
        val newExpiredAt = Instant.now().plusSeconds(14 * 24 * 60 * 60)

        refreshTokenService.save(RefreshToken(user.id, newRefreshTokenValue, newExpiredAt))
        return newAccessToken to newRefreshTokenValue
    }

    @Transactional(readOnly = true)
    fun findUserByEmail(email: String): User? {
        val user = userRepository.findByEmailAndIsDeletedFalse(email)
            ?: throw AuthException(
                code = "USER_NOT_FOUND",
                message = "사용자를 찾을 수 없습니다.",
                status = HttpStatus.UNAUTHORIZED
            )
        return user
    }

    @Transactional(readOnly = true)
    fun findUserById(userId: Long): User? {
        val user = userRepository.findByIdAndIsDeletedFalse(userId)
            ?: throw AuthException(
                code = "USER_NOT_FOUND",
                message = "사용자를 찾을 수 없습니다.",
                status = HttpStatus.UNAUTHORIZED
            )
        return user
    }
}
