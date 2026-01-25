package com.example.gitserver.module.user.application.command.service

import com.example.gitserver.common.jwt.JwtProvider
import com.example.gitserver.module.user.application.command.LoginUserCommand
import com.example.gitserver.module.user.application.command.handler.LoginUserCommandHandler
import com.example.gitserver.module.user.application.query.RefreshTokenQuery
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
class AuthCommandService(
    private val loginUserCommandHandler: LoginUserCommandHandler,
    private val refreshTokenQuery: RefreshTokenQuery,
    private val refreshTokenService: RefreshTokenService,
    private val userRepository: UserRepository,
    private val jwtProvider: JwtProvider
) {
    /**
     * 로그인 후 AccessToken과 RefreshToken을 생성하고 저장합니다.
     *
     * @param command 로그인 명령어
     * @param ipAddress 클라이언트 IP 주소
     * @param userAgent 클라이언트 User-Agent 정보
     * @return Triple<AccessToken, RefreshToken, User>
     */
    @Transactional
    fun login(command: LoginUserCommand, ipAddress: String, userAgent: String?): Triple<String, String, User> {
        val user = loginUserCommandHandler.handle(command, ipAddress, userAgent)
        val accessToken = jwtProvider.generateToken(user.id, user.email)
        val refreshTokenValue = UUID.randomUUID().toString()
        val expiredAt = Instant.now().plusSeconds(14 * 24 * 60 * 60)
        refreshTokenService.save(RefreshToken(user.id, refreshTokenValue, expiredAt))
        return Triple(accessToken, refreshTokenValue, user)
    }

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


    fun logout(userId: Long) {
        refreshTokenService.delete(userId)
    }
}