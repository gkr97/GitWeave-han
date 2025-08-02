package com.example.gitserver.module.user.application.command.handler


import com.example.gitserver.module.user.application.command.RegisterUserCommand
import com.example.gitserver.module.user.domain.User
import com.example.gitserver.module.user.exception.RegisterUserException
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.springframework.context.support.MessageSourceAccessor
import org.springframework.security.crypto.password.PasswordEncoder

class RegisterUserCommandHandlerTest {

    private val userRepository = mock(UserRepository::class.java)
    private val passwordEncoder = mock(PasswordEncoder::class.java)
    private val messageSourceAccessor = mock(MessageSourceAccessor::class.java)

    private val handler = RegisterUserCommandHandler(
        userRepository,
        passwordEncoder,
        messageSourceAccessor,
        codeCacheService = mock(),
    )

    @Test
    fun `회원가입 성공`() {
        // given
        val command = RegisterUserCommand(
            email = "test@test.com",
            password = "plain-pw",
            name = "테스트"
        )
        `when`(userRepository.existsByEmailAndIsDeletedFalse(command.email)).thenReturn(false)
        `when`(passwordEncoder.encode(command.password)).thenReturn("encoded-pw")
        `when`(userRepository.save(any<User>())).thenAnswer { it.getArgument<User>(0) }

        // when
        val result = handler.handle(command)

        // then
        assertThat(result.email).isEqualTo("test@test.com")
        assertThat(result.passwordHash).isEqualTo("encoded-pw")
        assertThat(result.name).isEqualTo("테스트")
        assertThat(result.isActive).isTrue()
        assertThat(result.isDeleted).isFalse()
        verify(userRepository).save(any<User>())
    }

    @Test
    fun `이메일 중복시 예외 발생`() {
        // given
        val command = RegisterUserCommand(
            email = "dup@test.com",
            password = "pw",
            name = "중복"
        )
        `when`(userRepository.existsByEmailAndIsDeletedFalse(command.email)).thenReturn(true)
        `when`(messageSourceAccessor.getMessage(any<String>())).thenReturn("이미 존재하는 이메일입니다.")

        // when
        val exception = assertThrows<RegisterUserException> {
            handler.handle(command)
        }

        // then
        assertThat(exception.code).isEqualTo("EMAIL_ALREADY_EXISTS")
        assertThat(exception.message).isEqualTo("이미 존재하는 이메일입니다.")
    }

    @Test
    fun `유저 저장 실패시 예외 발생`() {
        // given
        val command = RegisterUserCommand(
            email = "error@test.com",
            password = "pw",
            name = "에러"
        )
        `when`(userRepository.existsByEmailAndIsDeletedFalse(command.email)).thenReturn(false)
        `when`(passwordEncoder.encode(command.password)).thenReturn("encoded-pw")
        `when`(userRepository.save(any<User>())).thenThrow(RuntimeException("DB 오류"))
        `when`(messageSourceAccessor.getMessage(any<String>())).thenReturn("사용자 저장 실패")

        // when
        val exception = assertThrows<RegisterUserException> {
            handler.handle(command)
        }

        // then
        assertThat(exception.code).isEqualTo("USER_SAVE_FAILED")
        assertThat(exception.message).isEqualTo("사용자 저장 실패")
    }
}
