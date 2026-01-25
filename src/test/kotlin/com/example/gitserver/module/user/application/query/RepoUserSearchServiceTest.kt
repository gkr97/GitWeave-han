package com.example.gitserver.module.user.application.query

import com.example.gitserver.fixture.RepositoryFixture
import com.example.gitserver.fixture.UserFixture
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import com.example.gitserver.module.repository.domain.Collaborator
import com.example.gitserver.module.repository.interfaces.dto.UserSearchResponse
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime

@ExtendWith(MockKExtension::class)
class RepoUserSearchServiceTest {

    @MockK
    lateinit var userRepository: UserRepository

    @MockK
    lateinit var collaboratorRepository: CollaboratorRepository

    @InjectMockKs
    lateinit var repoUserSearchService: RepoUserSearchService

    @Test
    fun `저장소에 참여하지 않은 사용자 검색 성공`() {
        // given
        val user1 = UserFixture.default(id = 1L, email = "a@test.com", name = "Alice")
        val user2 = UserFixture.default(id = 2L, email = "b@test.com", name = "Bob")
        val user3 = UserFixture.default(id = 3L, email = "c@test.com", name = "Charlie")

        val repoId = 100L
        val keyword = "a"

        every { userRepository.findByEmailContainingOrNameContainingAndIsDeletedFalse(keyword, keyword) } returns listOf(user1, user2, user3)
        every { collaboratorRepository.findAllByRepositoryId(repoId) } returns listOf(
            Collaborator(
                id = 10L,
                repository = RepositoryFixture.default(
                    id = repoId,
                    name = "TestRepo",
                    owner = user1,
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now(),
                ),
                user = user2,
                roleCodeId = 1L,
                invitedAt = LocalDateTime.now(),
                accepted = true
            )
        )

        // when
        val result: List<UserSearchResponse> = repoUserSearchService.searchUsers(repoId, keyword)

        // then
        result.size shouldBe 2
        result.map { it.id } shouldBe listOf(1L, 3L)
        result.map { it.name } shouldBe listOf("Alice", "Charlie")
    }

    @Test
    fun `검색 결과가 없으면 빈 리스트 반환`() {
        // given
        val repoId = 100L
        val keyword = "nonexistent"

        every { userRepository.findByEmailContainingOrNameContainingAndIsDeletedFalse(keyword, keyword) } returns emptyList()
        every { collaboratorRepository.findAllByRepositoryId(repoId) } returns emptyList()

        // when
        val result = repoUserSearchService.searchUsers(repoId, keyword)

        // then
        result.size shouldBe 0
    }
}
