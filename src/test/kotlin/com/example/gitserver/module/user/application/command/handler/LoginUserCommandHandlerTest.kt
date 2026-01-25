package com.example.gitserver.module.user.application.command.handler

import com.example.gitserver.fixture.UserFixture
import com.example.gitserver.module.user.application.command.LoginUserCommand
import com.example.gitserver.module.user.domain.User
import com.example.gitserver.module.user.exception.UserLoginException
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.support.MessageSourceAccessor
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder

@ExtendWith(MockKExtension::class)
class LoginUserCommandHandlerTest {

    @MockK
    lateinit var userRepository: UserRepository

    @MockK
    lateinit var passwordEncoder: PasswordEncoder

    @MockK
    lateinit var messageAccessor: MessageSourceAccessor

    @MockK
    lateinit var eventPublisher: ApplicationEventPublisher

    @InjectMockKs
    lateinit var handler: LoginUserCommandHandler

    @Test
    fun `로그인 성공 - 정상적인 이메일과 비밀번호`() {
        // given
        val user = UserFixture.default(
            id = 1L,
            email = "test@test.com",
            passwordHash = "encoded-password",
            emailVerified = true
        )
        val command = LoginUserCommand(
            email = "test@test.com",
            password = "password123"
        )

        every { userRepository.findByEmailAndIsDeletedFalse("test@test.com") } returns user
        every { passwordEncoder.matches("password123", "encoded-password") } returns true
        every { messageAccessor.getMessage(any<String>()) } returns "메시지"
        every { eventPublisher.publishEvent(any<Any>()) } just Runs

        // when
        val result = handler.handle(command, "127.0.0.1", "Mozilla/5.0")

        // then
        result shouldBe user
        verify(exactly = 1) { eventPublisher.publishEvent(any<Any>()) }
    }

    @Test
    fun `로그인 실패 - 존재하지 않는 이메일`() {
        // given
        val command = LoginUserCommand(
            email = "notfound@test.com",
            password = "password123"
        )

        every { userRepository.findByEmailAndIsDeletedFalse("notfound@test.com") } returns null
        every { messageAccessor.getMessage("user.login.fail.email") } returns "이메일이 존재하지 않습니다"

        // when & then
        val exception = shouldThrow<UserLoginException> {
            handler.handle(command, "127.0.0.1", null)
        }
        exception.code shouldBe "USER_NOT_FOUND"
        exception.status shouldBe HttpStatus.UNAUTHORIZED
        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }

    @Test
    fun `로그인 실패 - 비밀번호 불일치`() {
        // given
        val user = UserFixture.default(
            id = 1L,
            email = "test@test.com",
            passwordHash = "encoded-password",
            emailVerified = true
        )
        val command = LoginUserCommand(
            email = "test@test.com",
            password = "wrong-password"
        )

        every { userRepository.findByEmailAndIsDeletedFalse("test@test.com") } returns user
        every { passwordEncoder.matches("wrong-password", "encoded-password") } returns false
        every { messageAccessor.getMessage("user.login.fail.password") } returns "비밀번호가 일치하지 않습니다"

        // when & then
        val exception = shouldThrow<UserLoginException> {
            handler.handle(command, "127.0.0.1", null)
        }
        exception.code shouldBe "INVALID_PASSWORD"
        exception.status shouldBe HttpStatus.UNAUTHORIZED
        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }

    @Test
    fun `로그인 실패 - 이메일 미인증`() {
        // given
        val user = UserFixture.default(
            id = 1L,
            email = "test@test.com",
            passwordHash = "encoded-password",
            emailVerified = false
        )
        val command = LoginUserCommand(
            email = "test@test.com",
            password = "password123"
        )

        every { userRepository.findByEmailAndIsDeletedFalse("test@test.com") } returns user
        every { passwordEncoder.matches("password123", "encoded-password") } returns true
        every { messageAccessor.getMessage("user.email.not-verified") } returns "이메일 인증이 필요합니다"

        // when & then
        val exception = shouldThrow<UserLoginException> {
            handler.handle(command, "127.0.0.1", null)
        }
        exception.code shouldBe "EMAIL_NOT_VERIFIED"
        exception.status shouldBe HttpStatus.FORBIDDEN
        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }
}
