package com.example.gitserver.module.user.application.command.service

import com.example.gitserver.common.jwt.JwtProvider
import com.example.gitserver.fixture.UserFixture
import com.example.gitserver.module.user.application.command.LoginUserCommand
import com.example.gitserver.module.user.application.command.handler.LoginUserCommandHandler
import com.example.gitserver.module.user.application.query.RefreshTokenQuery
import com.example.gitserver.module.user.domain.User
import com.example.gitserver.module.user.domain.vo.RefreshToken
import com.example.gitserver.module.user.exception.AuthException
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpStatus
import java.time.Instant

@ExtendWith(MockKExtension::class)
class AuthCommandServiceTest {

    @MockK
    lateinit var loginUserCommandHandler: LoginUserCommandHandler

    @MockK
    lateinit var refreshTokenQuery: RefreshTokenQuery

    @MockK
    lateinit var refreshTokenService: RefreshTokenService

    @MockK
    lateinit var userRepository: UserRepository

    @MockK
    lateinit var jwtProvider: JwtProvider

    @InjectMockKs
    lateinit var service: AuthCommandService

    @Test
    fun `로그인 성공 - AccessToken과 RefreshToken 생성`() {
        // given
        val user = UserFixture.default(
            id = 1L,
            email = "test@test.com"
        )
        val command = LoginUserCommand(
            email = "test@test.com",
            password = "password123"
        )

        every { loginUserCommandHandler.handle(command, "127.0.0.1", null) } returns user
        every { jwtProvider.generateToken(1L, "test@test.com") } returns "access-token"
        every { refreshTokenService.save(any()) } just Runs

        // when
        val (accessToken, refreshToken, returnedUser) = service.login(command, "127.0.0.1", null)

        // then
        accessToken shouldBe "access-token"
        refreshToken shouldNotBe null
        returnedUser shouldBe user
        verify(exactly = 1) { refreshTokenService.save(any()) }
    }

    @Test
    fun `RefreshToken으로 AccessToken 갱신 성공`() {
        // given
        val user = UserFixture.default(id = 1L, email = "test@test.com")
        val refreshToken = RefreshToken(
            userId = 1L,
            value = "refresh-token-value",
            expiredAt = Instant.now().plusSeconds(3600)
        )

        every { refreshTokenQuery.findByValue("refresh-token-value") } returns refreshToken
        every { userRepository.findByIdAndIsDeletedFalse(1L) } returns user
        every { jwtProvider.generateToken(1L, "test@test.com") } returns "new-access-token"
        every { refreshTokenService.save(any()) } just Runs

        // when
        val (newAccessToken, newRefreshToken) = service.refreshByRefreshToken("refresh-token-value")

        // then
        newAccessToken shouldBe "new-access-token"
        newRefreshToken shouldNotBe null
        verify(exactly = 1) { refreshTokenService.save(any()) }
    }

    @Test
    fun `RefreshToken 갱신 실패 - 토큰 없음`() {
        // given
        every { refreshTokenQuery.findByValue("invalid-token") } returns null

        // when & then
        val exception = shouldThrow<AuthException> {
            service.refreshByRefreshToken("invalid-token")
        }
        exception.code shouldBe "REFRESH_TOKEN_NOT_FOUND"
        exception.status shouldBe HttpStatus.UNAUTHORIZED
    }

    @Test
    fun `RefreshToken 갱신 실패 - 토큰 만료`() {
        // given
        val expiredToken = RefreshToken(
            userId = 1L,
            value = "expired-token",
            expiredAt = Instant.now().minusSeconds(3600)
        )

        every { refreshTokenQuery.findByValue("expired-token") } returns expiredToken

        // when & then
        val exception = shouldThrow<AuthException> {
            service.refreshByRefreshToken("expired-token")
        }
        exception.code shouldBe "REFRESH_TOKEN_EXPIRED"
        exception.status shouldBe HttpStatus.UNAUTHORIZED
    }

    @Test
    fun `로그아웃 성공`() {
        // given
        every { refreshTokenService.delete(1L) } just Runs

        // when
        service.logout(1L)

        // then
        verify(exactly = 1) { refreshTokenService.delete(1L) }
    }
}
