package com.example.gitserver.module.pullrequest.application.command.handler

import com.example.gitserver.common.util.GitRefUtils
import com.example.gitserver.fixture.PullRequestFixture
import com.example.gitserver.fixture.RepositoryFixture
import com.example.gitserver.fixture.UserFixture
import com.example.gitserver.module.pullrequest.application.command.CreatePullRequestCommand
import com.example.gitserver.module.pullrequest.domain.CodeBook
import com.example.gitserver.module.pullrequest.domain.PrStatus
import com.example.gitserver.module.pullrequest.domain.PullRequest
import com.example.gitserver.module.pullrequest.exception.*
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestRepository
import com.example.gitserver.module.pullrequest.infrastructure.support.GitRepoPolicyAdapter
import com.example.gitserver.module.repository.domain.Repository
import com.example.gitserver.module.repository.exception.RepositoryNotFoundException
import com.example.gitserver.module.repository.exception.UserNotFoundException
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.user.domain.User
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
class CreatePullRequestHandlerTest {

    @MockK
    lateinit var repositoryRepository: RepositoryRepository

    @MockK
    lateinit var userRepository: UserRepository

    @MockK
    lateinit var pullRequestRepository: PullRequestRepository

    @MockK
    lateinit var codes: CodeBook

    @MockK
    lateinit var policy: GitRepoPolicyAdapter

    @MockK
    lateinit var events: ApplicationEventPublisher

    @InjectMockKs
    lateinit var handler: CreatePullRequestHandler

    @Test
    fun `PR 생성 성공`() {
        // given
        val owner = UserFixture.default(id = 1L, email = "owner@test.com")
        val author = UserFixture.default(id = 2L, email = "author@test.com")
        val repo = RepositoryFixture.default(
            id = 1L,
            owner = owner,
            name = "test-repo"
        )
        val command = CreatePullRequestCommand(
            repositoryId = 1L,
            authorId = 2L,
            title = "새로운 기능 추가",
            description = "설명",
            sourceBranch = "feature",
            targetBranch = "main"
        )
        val sourceFull = GitRefUtils.toFullRef("feature")
        val targetFull = GitRefUtils.toFullRef("main")
        val savedPr = PullRequestFixture.default(
            id = 1L,
            repository = repo,
            author = author,
            title = "새로운 기능 추가",
            sourceBranch = sourceFull,
            targetBranch = targetFull
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo
        every { userRepository.findByIdAndIsDeletedFalse(2L) } returns author
        every { policy.isCollaboratorOrOwner(1L, 2L) } returns true
        every { policy.branchExists(1L, sourceFull) } returns true
        every { policy.branchExists(1L, targetFull) } returns true
        every { codes.prStatusId(PrStatus.OPEN) } returns 1L
        every { pullRequestRepository.existsByRepositoryIdAndSourceBranchAndTargetBranchAndStatusCodeId(1L, sourceFull, targetFull, 1L) } returns false
        every { policy.getHeadCommitHash(1L, targetFull) } returns "base123"
        every { policy.getHeadCommitHash(1L, sourceFull) } returns "head456"
        every { pullRequestRepository.save(any()) } returns savedPr
        every { events.publishEvent(any<Any>()) } just Runs

        // when
        val result = handler.handle(command)

        // then
        result shouldBe 1L
        verify(exactly = 1) { pullRequestRepository.save(any()) }
        verify(exactly = 1) { events.publishEvent(any<Any>()) }
    }

    @Test
    fun `PR 생성 실패 - 저장소 없음`() {
        // given
        val command = CreatePullRequestCommand(
            repositoryId = 999L,
            authorId = 1L,
            title = "PR",
            sourceBranch = "feature",
            targetBranch = "main",
            description = "test"
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(999L) } returns null

        // when & then
        shouldThrow<RepositoryNotFoundException> {
            handler.handle(command)
        }
    }

    @Test
    fun `PR 생성 실패 - 사용자 없음`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val command = CreatePullRequestCommand(
            repositoryId = 1L,
            authorId = 999L,
            title = "PR",
            sourceBranch = "feature",
            targetBranch = "main",
            description = "test"
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo
        every { userRepository.findByIdAndIsDeletedFalse(999L) } returns null

        // when & then
        shouldThrow<UserNotFoundException> {
            handler.handle(command)
        }
    }

    @Test
    fun `PR 생성 실패 - 권한 없음`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val author = UserFixture.default(id = 2L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val command = CreatePullRequestCommand(
            repositoryId = 1L,
            authorId = 2L,
            title = "PR",
            sourceBranch = "feature",
            targetBranch = "main",
            description = "test"
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo
        every { userRepository.findByIdAndIsDeletedFalse(2L) } returns author
        every { policy.isCollaboratorOrOwner(1L, 2L) } returns false

        // when & then
        shouldThrow<PermissionDenied> {
            handler.handle(command)
        }
    }

    @Test
    fun `PR 생성 실패 - 같은 브랜치`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val author = UserFixture.default(id = 2L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val command = CreatePullRequestCommand(
            repositoryId = 1L,
            authorId = 2L,
            title = "PR",
            sourceBranch = "main",
            targetBranch = "main",
            description = "test"
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo
        every { userRepository.findByIdAndIsDeletedFalse(2L) } returns author
        every { policy.isCollaboratorOrOwner(1L, 2L) } returns true

        // when & then
        shouldThrow<SameBranchNotAllowed> {
            handler.handle(command)
        }
    }

    @Test
    fun `PR 생성 실패 - 브랜치 없음`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val author = UserFixture.default(id = 2L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val command = CreatePullRequestCommand(
            repositoryId = 1L,
            authorId = 2L,
            title = "PR",
            sourceBranch = "nonexistent",
            targetBranch = "main",
            description = "test"
        )
        val sourceFull = GitRefUtils.toFullRef("nonexistent")

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo
        every { userRepository.findByIdAndIsDeletedFalse(2L) } returns author
        every { policy.isCollaboratorOrOwner(1L, 2L) } returns true
        every { policy.branchExists(1L, sourceFull) } returns false

        // when & then
        shouldThrow<BranchNotFound> {
            handler.handle(command)
        }
    }

    @Test
    fun `PR 생성 실패 - 중복된 열린 PR`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val author = UserFixture.default(id = 2L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val command = CreatePullRequestCommand(
            repositoryId = 1L,
            authorId = 2L,
            title = "PR",
            sourceBranch = "feature",
            targetBranch = "main",
            description = "test"
        )
        val sourceFull = GitRefUtils.toFullRef("feature")
        val targetFull = GitRefUtils.toFullRef("main")

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo
        every { userRepository.findByIdAndIsDeletedFalse(2L) } returns author
        every { policy.isCollaboratorOrOwner(1L, 2L) } returns true
        every { policy.branchExists(1L, sourceFull) } returns true
        every { policy.branchExists(1L, targetFull) } returns true
        every { codes.prStatusId(PrStatus.OPEN) } returns 1L
        every { pullRequestRepository.existsByRepositoryIdAndSourceBranchAndTargetBranchAndStatusCodeId(1L, sourceFull, targetFull, 1L) } returns true

        // when & then
        shouldThrow<DuplicateOpenPr> {
            handler.handle(command)
        }
    }
}
