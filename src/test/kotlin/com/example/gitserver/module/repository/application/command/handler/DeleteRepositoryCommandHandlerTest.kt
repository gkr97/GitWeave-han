package com.example.gitserver.module.repository.application.command.handler

import com.example.gitserver.fixture.RepositoryFixture
import com.example.gitserver.fixture.UserFixture
import com.example.gitserver.module.repository.application.command.DeleteRepositoryCommand
import com.example.gitserver.module.repository.exception.RepositoryNotFoundException
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
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

@ExtendWith(MockKExtension::class)
class DeleteRepositoryCommandHandlerTest {

    @MockK
    lateinit var repositoryRepository: RepositoryRepository

    @MockK
    lateinit var userRepository: UserRepository

    @MockK
    lateinit var access: com.example.gitserver.module.repository.domain.policy.RepoAccessPolicy

    @MockK
    lateinit var events: ApplicationEventPublisher

    @InjectMockKs
    lateinit var handler: DeleteRepositoryCommandHandler

    @Test
    fun `저장소 삭제 성공`() {
        // given
        val owner = UserFixture.default(id = 1L, email = "owner@test.com")
        val repo = RepositoryFixture.default(
            id = 1L,
            owner = owner,
            isDeleted = false
        )
        val command = DeleteRepositoryCommand(
            repositoryId = 1L,
            requesterEmail = "owner@test.com"
        )

        every { userRepository.findByEmailAndIsDeletedFalse("owner@test.com") } returns owner
        every { repositoryRepository.findById(1L) } returns java.util.Optional.of(repo)
        every { access.isOwner(1L, 1L) } returns true
        every { repositoryRepository.save(any()) } returns repo
        every { events.publishEvent(any<Any>()) } just Runs

        // when
        handler.handle(command)

        // then
        repo.isDeleted shouldBe true
        verify(exactly = 1) { repositoryRepository.save(repo) }
        verify(exactly = 1) { events.publishEvent(any<Any>()) }
    }

    @Test
    fun `저장소 삭제 실패 - 소유자가 아님`() {
        // given
        val owner = UserFixture.default(id = 1L, email = "owner@test.com")
        val requester = UserFixture.default(id = 2L, email = "requester@test.com")
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val command = DeleteRepositoryCommand(
            repositoryId = 1L,
            requesterEmail = "requester@test.com"
        )

        every { userRepository.findByEmailAndIsDeletedFalse("requester@test.com") } returns requester
        every { repositoryRepository.findById(1L) } returns java.util.Optional.of(repo)
        every { access.isOwner(1L, 2L) } returns false

        // when & then
        shouldThrow<IllegalAccessException> {
            handler.handle(command)
        }
    }

    @Test
    fun `저장소 삭제 실패 - 이미 삭제됨`() {
        // given
        val owner = UserFixture.default(id = 1L, email = "owner@test.com")
        val repo = RepositoryFixture.default(
            id = 1L,
            owner = owner,
            isDeleted = true
        )
        val command = DeleteRepositoryCommand(
            repositoryId = 1L,
            requesterEmail = "owner@test.com"
        )

        every { userRepository.findByEmailAndIsDeletedFalse("owner@test.com") } returns owner
        every { repositoryRepository.findById(1L) } returns java.util.Optional.of(repo)
        every { access.isOwner(1L, 1L) } returns true

        // when & then
        shouldThrow<IllegalStateException> {
            handler.handle(command)
        }
    }
}
