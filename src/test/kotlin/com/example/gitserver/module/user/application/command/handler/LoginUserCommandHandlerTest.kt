package com.example.gitserver.module.user.application.command.handler

import com.example.gitserver.module.user.application.command.LoginUserCommand
import com.example.gitserver.module.user.domain.User
import com.example.gitserver.module.user.exception.UserLoginException
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.springframework.context.support.MessageSourceAccessor
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder

class LoginUserCommandHandlerTest {

    private val userRepository = mock(UserRepository::class.java)
    private val passwordEncoder = mock(PasswordEncoder::class.java)
    private val messageAccessor = mock(MessageSourceAccessor::class.java)
    private val eventPublisher = mock(org.springframework.context.ApplicationEventPublisher::class.java)

    private val handler = LoginUserCommandHandler(
        userRepository,
        passwordEncoder,
        messageAccessor,
        eventPublisher
    )

    private val dummyIp = "127.0.0.1"
    private val dummyUa = "JUnit"

    @Test
    fun `로그인 성공시 유저 반환`() {
        // given
        val user = User(
            id = 1L,
            email = "user@a.com",
            passwordHash = "encoded-password",
            emailVerified = true,
            isActive = true,
            isDeleted = false
        )
        `when`(userRepository.findByEmail("user@a.com")).thenReturn(user)
        `when`(passwordEncoder.matches("plain-pw", "encoded-password")).thenReturn(true)

        val command = LoginUserCommand("user@a.com", "plain-pw")

        // when
        val result = handler.handle(command, dummyIp, dummyUa)

        // then
        assertThat(result).isEqualTo(user)
    }

    @Test
    fun `존재하지 않는 이메일로 로그인시 예외`() {
        `when`(userRepository.findByEmail("notfound@a.com")).thenReturn(null)
        `when`(messageAccessor.getMessage(any<String>())).thenReturn("가입된 이메일이 없습니다.")

        val command = LoginUserCommand("notfound@a.com", "pw")

        val exception = assertThrows<UserLoginException> {
            handler.handle(command, dummyIp, dummyUa)
        }
        assertThat(exception.code).isEqualTo("USER_NOT_FOUND")
        assertThat(exception.status).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(exception.message).isEqualTo("가입된 이메일이 없습니다.")
    }

    @Test
    fun `비밀번호 불일치시 예외`() {
        val user = User(
            id = 2L,
            email = "user@a.com",
            passwordHash = "encoded-pw",
            emailVerified = true,
            isActive = true,
            isDeleted = false
        )
        `when`(userRepository.findByEmail("user@a.com")).thenReturn(user)
        `when`(passwordEncoder.matches("wrong", "encoded-pw")).thenReturn(false)
        `when`(messageAccessor.getMessage(any<String>())).thenReturn("비밀번호가 올바르지 않습니다.")

        val command = LoginUserCommand("user@a.com", "wrong")

        val exception = assertThrows<UserLoginException> {
            handler.handle(command, dummyIp, dummyUa)
        }
        assertThat(exception.code).isEqualTo("INVALID_PASSWORD")
        assertThat(exception.status).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(exception.message).isEqualTo("비밀번호가 올바르지 않습니다.")
    }

    @Test
    fun `이메일 미인증시 예외`() {
        val user = User(
            id = 3L,
            email = "user@a.com",
            passwordHash = "encoded-pw",
            emailVerified = false,
            isActive = true,
            isDeleted = false
        )
        `when`(userRepository.findByEmail("user@a.com")).thenReturn(user)
        `when`(passwordEncoder.matches("pw", "encoded-pw")).thenReturn(true)
        `when`(messageAccessor.getMessage(any<String>())).thenReturn("이메일 인증이 필요합니다.")

        val command = LoginUserCommand("user@a.com", "pw")

        val exception = assertThrows<UserLoginException> {
            handler.handle(command, dummyIp, dummyUa)
        }
        assertThat(exception.code).isEqualTo("EMAIL_NOT_VERIFIED")
        assertThat(exception.status).isEqualTo(HttpStatus.FORBIDDEN)
        assertThat(exception.message).isEqualTo("이메일 인증이 필요합니다.")
    }
}
