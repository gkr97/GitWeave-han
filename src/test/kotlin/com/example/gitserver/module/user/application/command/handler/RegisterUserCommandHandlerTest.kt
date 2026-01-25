package com.example.gitserver.module.user.application.command.handler

import com.example.gitserver.fixture.UserFixture
import com.example.gitserver.module.common.application.service.CommonCodeCacheService
import com.example.gitserver.module.common.domain.CommonCodeDetail
import com.example.gitserver.module.common.dto.CommonCodeDetailResponse
import com.example.gitserver.module.user.application.command.RegisterUserCommand
import com.example.gitserver.module.user.application.service.IdenticonGenerator
import com.example.gitserver.module.user.domain.User
import com.example.gitserver.module.user.exception.RegisterUserException
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import com.example.gitserver.module.user.infrastructure.s3.S3Uploader
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.support.MessageSourceAccessor
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Duration

@ExtendWith(MockKExtension::class)
class RegisterUserCommandHandlerTest {

    @MockK
    lateinit var userRepository: UserRepository

    @MockK
    lateinit var passwordEncoder: PasswordEncoder

    @MockK
    lateinit var messageSourceAccessor: MessageSourceAccessor

    @MockK
    lateinit var codeCacheService: CommonCodeCacheService

    @MockK
    lateinit var identiconGenerator: IdenticonGenerator

    @MockK
    lateinit var s3Uploader: S3Uploader

    @InjectMockKs
    lateinit var handler: RegisterUserCommandHandler

    @Test
    fun `회원가입 성공 - 정상적인 입력`() {
        // given
        val command = RegisterUserCommand(
            email = "newuser@test.com",
            password = "Password123!",
            name = "새유저"
        )
        val savedUser = UserFixture.default(
            id = 1L,
            email = "newuser@test.com",
            name = "새유저",
            passwordHash = "encoded-password"
        )
        val providerCode = CommonCodeDetailResponse(
            id = 1L,
            code = "local",
            name = "로컬",
            sortOrder = 1,
            isActive = true
        )

        every { userRepository.existsByEmailAndIsDeletedFalse("newuser@test.com") } returns false
        every { codeCacheService.getCodeDetailsOrLoad("PROVIDER") } returns listOf(providerCode)
        every { passwordEncoder.encode("Password123!") } returns "encoded-password"
        every { userRepository.save(any()) } returns savedUser
        every { identiconGenerator.generate(any(), any()) } returns ByteArray(100)
        every { s3Uploader.uploadBytesAndGetPresignedGetUrl(any(), any(), any(), any()) } returns "https://s3.url/image.png"
        every { userRepository.save(any()) } returns savedUser

        // when
        val result = handler.handle(command)

        // then
        result shouldNotBe null
        result.email shouldBe "newuser@test.com"
        result.name shouldBe "새유저"
        verify(exactly = 2) { userRepository.save(any()) }
        verify(exactly = 1) { identiconGenerator.generate(any(), any()) }
    }

    @Test
    fun `회원가입 실패 - 이미 존재하는 이메일`() {
        // given
        val command = RegisterUserCommand(
            email = "existing@test.com",
            password = "Password123!",
            name = "기존유저"
        )

        every { userRepository.existsByEmailAndIsDeletedFalse("existing@test.com") } returns true
        every { messageSourceAccessor.getMessage("user.email.exists") } returns "이미 존재하는 이메일입니다"

        // when & then
        val exception = shouldThrow<RegisterUserException> {
            handler.handle(command)
        }
        exception.code shouldBe "EMAIL_ALREADY_EXISTS"
        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `회원가입 성공 - identicon 생성 실패해도 계속 진행`() {
        // given
        val command = RegisterUserCommand(
            email = "newuser@test.com",
            password = "Password123!",
            name = "새유저"
        )
        val savedUser = UserFixture.default(
            id = 1L,
            email = "newuser@test.com",
            name = "새유저",
            passwordHash = "encoded-password"
        )
        val providerCode = CommonCodeDetailResponse(
            id = 1L,
            code = "local",
            name = "로컬",
            sortOrder = 1,
            isActive = true
        )

        every { userRepository.existsByEmailAndIsDeletedFalse("newuser@test.com") } returns false
        every { codeCacheService.getCodeDetailsOrLoad("PROVIDER") } returns listOf(providerCode)
        every { passwordEncoder.encode("Password123!") } returns "encoded-password"
        every { userRepository.save(any()) } returns savedUser
        every { identiconGenerator.generate(any(), any()) } throws RuntimeException("Identicon 생성 실패")
        every { userRepository.save(any()) } returns savedUser

        // when
        val result = handler.handle(command)

        // then
        result shouldNotBe null
        result.email shouldBe "newuser@test.com"
        verify(exactly = 1) { userRepository.save(any()) }
    }
}
