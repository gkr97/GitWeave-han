package com.example.gitserver.module.user.application.query

import com.example.gitserver.fixture.UserFixture
import com.example.gitserver.module.repository.interfaces.dto.UserSearchResponse
import com.example.gitserver.module.user.domain.User
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import io.kotest.matchers.shouldBe
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.PageImpl

@ExtendWith(MockKExtension::class)
class UserSearchQueryServiceTest {

    @MockK
    lateinit var userRepository: UserRepository

    @InjectMockKs
    lateinit var service: UserSearchQueryService

    @Test
    fun `사용자 검색 성공`() {
        // given
        val user1 = UserFixture.default(id = 1L, email = "alice@test.com", name = "Alice")
        val user2 = UserFixture.default(id = 2L, email = "bob@test.com", name = "Bob")
        val users = listOf(user1, user2)

        every { userRepository.searchAllByKeyword("alice", PageRequest.of(0, 20)) } returns users

        // when
        val result = service.search("alice")

        // then
        result.size shouldBe 2
        result[0].email shouldBe "alice@test.com"
    }

    @Test
    fun `사용자 검색 - 빈 키워드면 빈 리스트`() {
        // when
        val result = service.search("   ")

        // then
        result.size shouldBe 0
        verify(exactly = 0) { userRepository.searchAllByKeyword(any(), any()) }
    }

    @Test
    fun `사용자 검색 - 특정 사용자 제외`() {
        // given
        val user1 = UserFixture.default(id = 1L, email = "alice@test.com", name = "Alice")
        val user2 = UserFixture.default(id = 2L, email = "bob@test.com", name = "Bob")
        val users = listOf(user1, user2)

        every { userRepository.searchAllByKeyword(any(), any()) } returns users
        // when
        val result = service.search("test", limit = 20, excludeUserId = 1L)

        // then
        result.size shouldBe 1
        result[0].id shouldBe 2L
    }
}
