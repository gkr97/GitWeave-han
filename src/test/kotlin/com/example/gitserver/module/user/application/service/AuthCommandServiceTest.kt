package com.example.gitserver.module.user.application.service

import com.example.gitserver.common.jwt.JwtProvider
import com.example.gitserver.module.user.application.command.LoginUserCommand
import com.example.gitserver.module.user.application.command.service.RefreshTokenService
import com.example.gitserver.module.user.application.command.handler.LoginUserCommandHandler
import com.example.gitserver.module.user.application.command.service.AuthCommandService
import com.example.gitserver.module.user.domain.User
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.Instant

class AuthCommandServiceTest {

    private val loginUserCommandHandler: LoginUserCommandHandler = mock()
    private val refreshTokenService: RefreshTokenService = mock()
    private val jwtProvider: JwtProvider = mock()

    private val authCommandService = AuthCommandService(
        loginUserCommandHandler,
        refreshTokenService,
        jwtProvider
    )

    @Test
    fun `login 정상 동작 - 토큰 발급 및 저장`() {
        // given
        val user = User(
            id = 1L,
            email = "user@test.com",
            passwordHash = "hashed",
        )
        val command = LoginUserCommand(email = user.email, password = "rawPassword")
        val ipAddress = "127.0.0.1"
        val userAgent = "JUnit"

        whenever(loginUserCommandHandler.handle(command, ipAddress, userAgent)).thenReturn(user)
        whenever(jwtProvider.generateToken(user.id, user.email)).thenReturn("ACCESS_TOKEN")

        // when
        val (accessToken, refreshTokenValue, resultUser) = authCommandService.login(command, ipAddress, userAgent)

        // then
        assertEquals("ACCESS_TOKEN", accessToken)
        assertEquals(user, resultUser)
        assertNotNull(refreshTokenValue)

        verify(loginUserCommandHandler, times(1)).handle(command, ipAddress, userAgent)
        verify(jwtProvider, times(1)).generateToken(user.id, user.email)
        verify(refreshTokenService, times(1)).save(
            check {
                assertEquals(user.id, it.userId)
                assertEquals(refreshTokenValue, it.value)
                assertTrue(it.expiredAt.isAfter(Instant.now()))
            }
        )
    }
}
