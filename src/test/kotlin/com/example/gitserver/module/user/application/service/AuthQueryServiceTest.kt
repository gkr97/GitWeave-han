package com.example.gitserver.module.user.application.service

import com.example.gitserver.common.jwt.JwtProvider
import com.example.gitserver.module.user.application.command.RefreshTokenCommand
import com.example.gitserver.module.user.application.query.RefreshTokenQuery
import com.example.gitserver.module.user.domain.vo.RefreshToken
import com.example.gitserver.module.user.exception.AuthException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.*

class AuthQueryServiceTest {

    private val refreshTokenQuery: RefreshTokenQuery = mock()
    private val refreshTokenCommand: RefreshTokenCommand = mock()
    private val jwtProvider: JwtProvider = mock()

    private val authQueryService = AuthQueryService(
        refreshTokenQuery,
        refreshTokenCommand,
        jwtProvider
    )

    @Test
    fun `정상적인 리프레시 토큰으로 새 토큰 발급`() {
        // given
        val userId = 1L
        val email = "test@test.com"
        val oldRefreshToken = "OLD_REFRESH_TOKEN"
        val newAccessToken = "NEW_ACCESS_TOKEN"
        val newRefreshToken = "NEW_REFRESH_TOKEN"
        val expiredAt = Instant.now().plusSeconds(14 * 24 * 60 * 60)
        val savedToken = RefreshToken(userId, oldRefreshToken, expiredAt)

        whenever(refreshTokenQuery.findByUserId(userId)).thenReturn(savedToken)
        whenever(jwtProvider.generateToken(userId, email)).thenReturn(newAccessToken)

        // when
        val (accessToken, refreshToken) = authQueryService.refresh(userId, email, oldRefreshToken)

        // then
        assertEquals(newAccessToken, accessToken)
        assertNotNull(refreshToken)
        verify(refreshTokenCommand).save(
            check {
                assertEquals(userId, it.userId)
                assertEquals(refreshToken, it.value)
                assertTrue(it.expiredAt.isAfter(Instant.now()))
            }
        )
    }

    @Test
    fun `저장된 토큰이 없으면 예외`() {
        // given
        val userId = 1L
        val email = "test@test.com"
        val oldRefreshToken = "NOT_EXIST_TOKEN"

        whenever(refreshTokenQuery.findByUserId(userId)).thenReturn(null)

        // when & then
        val ex = assertThrows(AuthException::class.java) {
            authQueryService.refresh(userId, email, oldRefreshToken)
        }
        assertEquals("REFRESH_TOKEN_NOT_FOUND", ex.code)
        assertEquals(HttpStatus.UNAUTHORIZED, ex.status)
    }

    @Test
    fun `리프레시 토큰 값이 일치하지 않으면 예외`() {
        // given
        val userId = 1L
        val email = "test@test.com"
        val oldRefreshToken = "OLD_REFRESH_TOKEN"
        val savedToken = RefreshToken(userId, "DIFFERENT_TOKEN", Instant.now().plusSeconds(10))

        whenever(refreshTokenQuery.findByUserId(userId)).thenReturn(savedToken)

        // when & then
        val ex = assertThrows(AuthException::class.java) {
            authQueryService.refresh(userId, email, oldRefreshToken)
        }
        assertEquals("REFRESH_TOKEN_MISMATCH", ex.code)
        assertEquals(HttpStatus.UNAUTHORIZED, ex.status)
    }
}
