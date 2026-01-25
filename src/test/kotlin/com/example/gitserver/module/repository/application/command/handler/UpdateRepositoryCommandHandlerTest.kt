package com.example.gitserver.module.repository.application.command.handler

import com.example.gitserver.fixture.RepositoryFixture
import com.example.gitserver.fixture.UserFixture
import com.example.gitserver.module.common.cache.RepoCacheEvictor
import com.example.gitserver.module.gitindex.domain.port.GitRepositoryPort
import com.example.gitserver.module.repository.application.command.UpdateRepositoryCommand
import com.example.gitserver.module.repository.domain.Repository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@ExtendWith(MockKExtension::class)
class UpdateRepositoryCommandHandlerTest {

    @MockK
    lateinit var repositoryRepository: RepositoryRepository

    @MockK
    lateinit var gitRepositoryPort: GitRepositoryPort

    @MockK
    lateinit var access: com.example.gitserver.module.repository.domain.policy.RepoAccessPolicy

    @MockK
    lateinit var events: ApplicationEventPublisher

    @InjectMockKs
    lateinit var handler: UpdateRepositoryCommandHandler

    @BeforeEach
    fun setUp() {
        mockkStatic(TransactionSynchronizationManager::class)
        every { TransactionSynchronizationManager.registerSynchronization(any<TransactionSynchronization>()) } just Runs
    }

    @Test
    fun `저장소 수정 성공 - 이름과 설명 변경`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val repo = RepositoryFixture.default(
            id = 1L,
            owner = owner,
            name = "old-name",
            description = "기존 설명"
        )
        val command = UpdateRepositoryCommand(
            repositoryId = 1L,
            requesterId = 1L,
            newName = "new-name",
            newDescription = "새 설명"
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo
        every { access.isOwner(1L, 1L) } returns true
        every { repositoryRepository.existsByOwnerIdAndNameAndIdNot(1L, "new-name", 1L) } returns false
        every { repositoryRepository.save(any()) } returns repo
        every { events.publishEvent(any<Any>()) } just Runs
        every { gitRepositoryPort.renameRepositoryDirectory(any(), any(), any()) } just Runs

        // when
        handler.handle(command)

        // then
        repo.name shouldBe "new-name"
        repo.description shouldBe "새 설명"
        verify(exactly = 1) { repositoryRepository.save(repo) }
        verify(exactly = 1) { events.publishEvent(any<Any>()) }
    }

    @Test
    fun `저장소 수정 실패 - 권한 없음`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val command = UpdateRepositoryCommand(
            repositoryId = 1L,
            requesterId = 2L,
            newName = "new-name",
            newDescription = "새 설명"
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo
        every { access.isOwner(1L, 2L) } returns false

        // when & then
        shouldThrow<IllegalAccessException> {
            handler.handle(command)
        }
    }

    @Test
    fun `저장소 수정 실패 - 중복된 이름`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val command = UpdateRepositoryCommand(
            repositoryId = 1L,
            requesterId = 1L,
            newName = "duplicate-name",
            newDescription = "설명"
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo
        every { access.isOwner(1L, 1L) } returns true
        every { repositoryRepository.existsByOwnerIdAndNameAndIdNot(1L, "duplicate-name", 1L) } returns true

        // when & then
        shouldThrow<IllegalArgumentException> {
            handler.handle(command)
        }
    }
}
