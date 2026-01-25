package com.example.gitserver.module.user.application.query

import com.example.gitserver.fixture.UserFixture
import com.example.gitserver.module.user.domain.User
import com.example.gitserver.module.user.exception.AuthException
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpStatus

@ExtendWith(MockKExtension::class)
class AuthQueryServiceTest {

    @MockK
    lateinit var userRepository: UserRepository

    @InjectMockKs
    lateinit var authQueryService: AuthQueryService

    @Test
    fun `email로 사용자 조회 성공`() {
        // given
        val user = UserFixture.default(
            id = 1L,
            email = "test@test.com",
            name = "테스트유저"
        )

        every { userRepository.findByEmailAndIsDeletedFalse("test@test.com") } returns user

        // when
        val result = authQueryService.findUserByEmail("test@test.com")

        // then
        result shouldBe user
    }

    @Test
    fun `email로 사용자 조회 실패하면 AuthException`() {
        // given
        every { userRepository.findByEmailAndIsDeletedFalse(any()) } returns null

        // when & then
        val exception = shouldThrow<AuthException> {
            authQueryService.findUserByEmail("notfound@test.com")
        }
        exception.code shouldBe "USER_NOT_FOUND"
        exception.status shouldBe HttpStatus.UNAUTHORIZED
    }

    @Test
    fun `id로 사용자 조회 성공`() {
        // given
        val user = UserFixture.default(
            id = 10L,
            email = "id@test.com",
            name = "아이디유저"
        )

        every { userRepository.findByIdAndIsDeletedFalse(10L) } returns user

        // when
        val result = authQueryService.findUserById(10L)

        // then
        result shouldBe user
    }

    @Test
    fun `id로 사용자 조회 실패하면 AuthException`() {
        // given
        every { userRepository.findByIdAndIsDeletedFalse(any()) } returns null

        // when & then
        val exception = shouldThrow<AuthException> {
            authQueryService.findUserById(999L)
        }
        exception.code shouldBe "USER_NOT_FOUND"
        exception.status shouldBe HttpStatus.UNAUTHORIZED
    }
}
