package com.example.gitserver.module.repository.application.command.handler

import com.example.gitserver.common.util.GitRefUtils
import com.example.gitserver.fixture.BranchFixture
import com.example.gitserver.fixture.RepositoryFixture
import com.example.gitserver.fixture.UserFixture
import com.example.gitserver.module.common.cache.RepoCacheEvictor
import com.example.gitserver.module.gitindex.domain.port.GitRepositoryPort
import com.example.gitserver.module.repository.application.command.CreateBranchCommand
import com.example.gitserver.module.repository.application.command.DeleteBranchCommand
import com.example.gitserver.module.repository.domain.Branch
import com.example.gitserver.module.repository.domain.Repository
import com.example.gitserver.module.repository.exception.*
import com.example.gitserver.module.repository.infrastructure.persistence.BranchRepository
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
import java.util.Optional

@ExtendWith(MockKExtension::class)
class BranchCommandHandlerTest {

    @MockK
    lateinit var branchRepository: BranchRepository

    @MockK
    lateinit var repositoryRepository: RepositoryRepository

    @MockK
    lateinit var gitRepositoryPort: GitRepositoryPort

    @MockK
    lateinit var evictor: RepoCacheEvictor

    @MockK
    lateinit var access: com.example.gitserver.module.repository.domain.policy.RepoAccessPolicy

    @MockK
    lateinit var events: ApplicationEventPublisher

    @InjectMockKs
    lateinit var handler: BranchCommandHandler


    @Test
    fun `브랜치 생성 성공`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val repo = RepositoryFixture.default(
            id = 1L,
            owner = owner,
            defaultBranch = "main"
        )
        val sourceBranch = BranchFixture.default(
            id = 1L,
            repository = repo,
            name = "refs/heads/main",
            headCommitHash = "abc123"
        )
        val command = CreateBranchCommand(
            repositoryId = 1L,
            branchName = "feature",
            sourceBranch = "main",
            requesterId = 1L
        )
        val newBranch = BranchFixture.default(
            id = 2L,
            repository = repo,
            name = "refs/heads/feature",
            isDefault = false
        )

        every { repositoryRepository.findById(1L) } returns Optional.of(repo)
        every { access.canWrite(1L, 1L) } returns true
        every { branchRepository.existsByRepositoryIdAndName(1L, GitRefUtils.toFullRef("feature")) } returns false
        every { branchRepository.findByRepositoryIdAndName(1L, GitRefUtils.toFullRef("main")) } returns sourceBranch
        every { gitRepositoryPort.createBranch(any(), "feature", "main") } just Runs
        every { branchRepository.save(any()) } returns newBranch
        every { evictor.evictRepoDetailAndBranches() } just Runs
        every { events.publishEvent(any<Any>()) } just Runs

        every { evictor.evictRepoDetailAndBranches() } just Runs
        every { evictor.evictRepoLists() } just Runs



        // when
        val result = handler.handle(command)

        // then
        result shouldBe 2L
        verify(exactly = 1) { gitRepositoryPort.createBranch(any(), "feature", "main") }
        verify(exactly = 1) { branchRepository.save(any()) }
    }

    @Test
    fun `브랜치 생성 실패 - 권한 없음`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val command = CreateBranchCommand(
            repositoryId = 1L,
            branchName = "feature",
            sourceBranch = null,
            requesterId = 2L
        )

        every { repositoryRepository.findById(1L) } returns java.util.Optional.of(repo)
        every { access.canWrite(1L, 2L) } returns false

        // when & then
        shouldThrow<RepositoryAccessDeniedException> {
            handler.handle(command)
        }
    }

    @Test
    fun `브랜치 생성 실패 - 이미 존재하는 브랜치`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val command = CreateBranchCommand(
            repositoryId = 1L,
            branchName = "feature",
            sourceBranch = null,
            requesterId = 1L
        )

        every { repositoryRepository.findById(1L) } returns java.util.Optional.of(repo)
        every { access.canWrite(1L, 1L) } returns true
        every { branchRepository.existsByRepositoryIdAndName(1L, GitRefUtils.toFullRef("feature")) } returns true

        // when & then
        shouldThrow<BranchAlreadyExistsException> {
            handler.handle(command)
        }
    }

    @Test
    fun `브랜치 삭제 성공`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val repo = RepositoryFixture.default(
            id = 1L,
            owner = owner,
            defaultBranch = "main"
        )
        val branch = BranchFixture.default(
            id = 1L,
            repository = repo,
            name = "refs/heads/feature",
            isDefault = false
        )
        val command = DeleteBranchCommand(
            repositoryId = 1L,
            branchName = "feature",
            requesterId = 1L
        )

        every { repositoryRepository.findById(1L) } returns java.util.Optional.of(repo)
        every { access.canWrite(1L, 1L) } returns true
        every { branchRepository.findByRepositoryIdAndName(1L, GitRefUtils.toFullRef("feature")) } returns branch
        every { gitRepositoryPort.deleteBranch(any(), "feature") } just Runs
        every { branchRepository.delete(any()) } just Runs
        every { events.publishEvent(any<Any>()) } just Runs

        // when
        handler.handle(command)

        // then
        verify(exactly = 1) { gitRepositoryPort.deleteBranch(any(), "feature") }
        verify(exactly = 1) { branchRepository.delete(branch) }
    }

    @Test
    fun `브랜치 삭제 실패 - 기본 브랜치는 삭제 불가`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val repo = RepositoryFixture.default(
            id = 1L,
            owner = owner,
            defaultBranch = "main"
        )
        val command = DeleteBranchCommand(
            repositoryId = 1L,
            branchName = "main",
            requesterId = 1L
        )

        every { repositoryRepository.findById(1L) } returns java.util.Optional.of(repo)
        every { access.canWrite(1L, 1L) } returns true

        // when & then
        shouldThrow<DefaultBranchDeletionNotAllowedException> {
            handler.handle(command)
        }
    }

    @Test
    fun `브랜치 삭제 실패 - 브랜치 없음`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val command = DeleteBranchCommand(
            repositoryId = 1L,
            branchName = "nonexistent",
            requesterId = 1L
        )

        every { repositoryRepository.findById(1L) } returns java.util.Optional.of(repo)
        every { access.canWrite(1L, 1L) } returns true
        every { branchRepository.findByRepositoryIdAndName(1L, GitRefUtils.toFullRef("nonexistent")) } returns null

        // when & then
        shouldThrow<BranchNotFoundException> {
            handler.handle(command)
        }
    }
}
