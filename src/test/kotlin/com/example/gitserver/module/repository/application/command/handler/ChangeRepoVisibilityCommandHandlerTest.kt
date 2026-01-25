package com.example.gitserver.module.repository.application.command.handler

import com.example.gitserver.fixture.RepositoryFixture
import com.example.gitserver.fixture.UserFixture
import com.example.gitserver.module.common.cache.RepoCacheEvictor
import com.example.gitserver.module.repository.application.command.ChangeRepositoryVisibilityCommand
import com.example.gitserver.module.repository.domain.CodeBook
import com.example.gitserver.module.repository.domain.event.RepositoryVisibilityChanged
import com.example.gitserver.module.repository.exception.RepositoryNotFoundException
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
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
class ChangeRepoVisibilityCommandHandlerTest {

    @MockK
    lateinit var repositoryRepository: RepositoryRepository

    @MockK
    lateinit var access: com.example.gitserver.module.repository.domain.policy.RepoAccessPolicy

    @MockK
    lateinit var codeBook: CodeBook

    @MockK
    lateinit var evictor: RepoCacheEvictor

    @MockK
    lateinit var events: ApplicationEventPublisher

    @InjectMockKs
    lateinit var handler: ChangeRepoVisibilityCommandHandler

    @Test
    fun `가시성 변경 성공`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val repo = RepositoryFixture.default(
            id = 1L,
            owner = owner,
            visibilityCodeId = 1L // PRIVATE
        )
        val command = ChangeRepositoryVisibilityCommand(
            repositoryId = 1L,
            requesterId = 1L,
            newVisibility = "PUBLIC"
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo
        every { access.isOwner(1L, 1L) } returns true
        every { codeBook.visibilityId("PUBLIC") } returns 2L
        every { events.publishEvent(any<RepositoryVisibilityChanged>()) } just Runs

        // when
        handler.handle(command)

        // then
        repo.visibilityCodeId shouldBe 2L
        verify(exactly = 1) { events.publishEvent(any<RepositoryVisibilityChanged>()) }
    }

    @Test
    fun `가시성 변경 실패 - 소유자가 아님`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val command = ChangeRepositoryVisibilityCommand(
            repositoryId = 1L,
            requesterId = 2L,
            newVisibility = "PUBLIC"
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo
        every { access.isOwner(1L, 2L) } returns false

        // when & then
        shouldThrow<SecurityException> {
            handler.handle(command)
        }
    }

    @Test
    fun `가시성 변경 실패 - 저장소 없음`() {
        // given
        val command = ChangeRepositoryVisibilityCommand(
            repositoryId = 999L,
            requesterId = 1L,
            newVisibility = "PUBLIC"
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(999L) } returns null

        // when & then
        shouldThrow<RepositoryNotFoundException> {
            handler.handle(command)
        }
    }
}
