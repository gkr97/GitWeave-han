package com.example.gitserver.module.user.application.service

import com.example.gitserver.common.jwt.JwtProvider
import com.example.gitserver.module.user.application.command.LoginUserCommand
import com.example.gitserver.module.user.application.command.RefreshTokenCommand
import com.example.gitserver.module.user.application.command.handler.LoginUserCommandHandler
import com.example.gitserver.module.user.domain.User
import com.example.gitserver.module.user.domain.vo.RefreshToken
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

@Service
class AuthCommandService(
    private val loginUserCommandHandler: LoginUserCommandHandler,
    private val refreshTokenCommand: RefreshTokenCommand,
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
        refreshTokenCommand.save(RefreshToken(user.id, refreshTokenValue, expiredAt))
        return Triple(accessToken, refreshTokenValue, user)
    }


    fun logout(userId: Long) {
        refreshTokenCommand.delete(userId)
    }
}