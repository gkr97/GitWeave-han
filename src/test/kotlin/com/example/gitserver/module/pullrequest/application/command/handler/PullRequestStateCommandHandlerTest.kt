package com.example.gitserver.module.pullrequest.application.command.handler

import com.example.gitserver.fixture.PullRequestFixture
import com.example.gitserver.fixture.RepositoryFixture
import com.example.gitserver.fixture.UserFixture
import com.example.gitserver.module.gitindex.domain.event.GitEvent
import com.example.gitserver.module.gitindex.domain.vo.MergeType
import com.example.gitserver.module.pullrequest.application.MergePullRequestCommand
import com.example.gitserver.module.pullrequest.application.command.ClosePullRequestCommand
import com.example.gitserver.module.pullrequest.application.command.ReopenPullRequestCommand
import com.example.gitserver.module.pullrequest.domain.CodeBook
import com.example.gitserver.module.pullrequest.domain.PrMergeType
import com.example.gitserver.module.pullrequest.domain.PrStatus
import com.example.gitserver.module.pullrequest.domain.PullRequest
import com.example.gitserver.module.pullrequest.exception.InvalidStateTransition
import com.example.gitserver.module.pullrequest.exception.MergeNotAllowed
import com.example.gitserver.module.pullrequest.exception.PermissionDenied
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestMergeLogRepository
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestRepository
import com.example.gitserver.module.repository.domain.Repository
import com.example.gitserver.module.repository.exception.RepositoryNotFoundException
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.user.exception.UserNotFoundException
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class PullRequestStateCommandHandlerTest {

    @MockK
    lateinit var repositoryRepository: RepositoryRepository

    @MockK
    lateinit var collaboratorRepository: CollaboratorRepository

    @MockK
    lateinit var userRepository: UserRepository

    @MockK
    lateinit var pullRequestRepository: PullRequestRepository

    @MockK
    lateinit var mergeLogRepository: PullRequestMergeLogRepository

    @MockK
    lateinit var codes: CodeBook

    @MockK
    lateinit var reviewCommandHandler: PullRequestReviewCommandHandler

    @MockK
    lateinit var gitRepositoryPort: com.example.gitserver.module.gitindex.domain.port.GitRepositoryPort

    @MockK
    lateinit var gitRepositorySyncHandler: com.example.gitserver.module.repository.application.command.handler.GitRepositorySyncHandler

    @MockK
    lateinit var gitEventPublisher: com.example.gitserver.module.repository.domain.event.GitEventPublisher

    @MockK
    lateinit var gitIndexEvictor: com.example.gitserver.module.gitindex.infrastructure.redis.GitIndexEvictor

    @InjectMockKs
    lateinit var handler: PullRequestStateCommandHandler

    @Test
    fun `PR 닫기 성공`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val author = UserFixture.default(id = 2L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val pr = PullRequestFixture.default(
            id = 1L,
            repository = repo,
            author = author,
            statusCodeId = 1L // OPEN
        )
        val command = ClosePullRequestCommand(
            repositoryId = 1L,
            pullRequestId = 1L,
            requesterId = 2L
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo
        every { userRepository.findByIdAndIsDeletedFalse(2L) } returns author
        every { pullRequestRepository.findById(1L) } returns java.util.Optional.of(pr)
        every { collaboratorRepository.existsByRepositoryIdAndUserId(1L, 2L) } returns false
        every { codes.prStatusId(PrStatus.OPEN) } returns 1L
        every { codes.prStatusId(PrStatus.CLOSED) } returns 2L
        every { pullRequestRepository.save(any()) } returns pr

        // when
        handler.handle(command)

        // then
        pr.statusCodeId shouldBe 2L
        pr.closedAt shouldNotBe null
        verify(exactly = 1) { pullRequestRepository.save(pr) }
    }

    @Test
    fun `PR 닫기 실패 - 이미 닫힌 PR`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val author = UserFixture.default(id = 2L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val pr = PullRequestFixture.default(
            id = 1L,
            repository = repo,
            author = author,
            statusCodeId = 2L // CLOSED
        )
        val command = ClosePullRequestCommand(
            repositoryId = 1L,
            pullRequestId = 1L,
            requesterId = 2L
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo
        every { userRepository.findByIdAndIsDeletedFalse(2L) } returns author
        every { pullRequestRepository.findById(1L) } returns java.util.Optional.of(pr)
        every { collaboratorRepository.existsByRepositoryIdAndUserId(1L, 2L) } returns false
        every { codes.prStatusId(PrStatus.OPEN) } returns 1L
        every { codes.prStatusId(PrStatus.CLOSED) } returns 2L

        // when & then
        shouldThrow<InvalidStateTransition> {
            handler.handle(command)
        }
    }

    @Test
    fun `PR 머지 성공`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val pr = PullRequestFixture.default(
            id = 1L,
            repository = repo,
            author = owner,
            statusCodeId = 1L // OPEN
        )
        val command = MergePullRequestCommand(
            repositoryId = 1L,
            pullRequestId = 1L,
            requesterId = 1L,
            mergeType = "merge_commit",
            message = "머지 메시지"
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo
        every { userRepository.findByIdAndIsDeletedFalse(1L) } returns owner
        every { pullRequestRepository.findById(1L) } returns java.util.Optional.of(pr)
        every { collaboratorRepository.existsByRepositoryIdAndUserId(1L, 1L) } returns true
        every { codes.prStatusId(PrStatus.OPEN) } returns 1L
        every { codes.prStatusId(PrStatus.MERGED) } returns 3L
        every { codes.toGitMergeType(any()) } returns MergeType.MERGE_COMMIT
        every { reviewCommandHandler.isMergeAllowed(1L) } returns true
        every { codes.prMergeTypeId(any()) } returns 1L
        every { codes.toGitMergeType(any()) } returns MergeType.MERGE_COMMIT
        every { gitRepositoryPort.getHeadCommitHash(any(), any()) } returns "old123"
        every { gitRepositoryPort.merge(any()) } returns "new456"
        every { pullRequestRepository.save(any()) } returns pr
        every { mergeLogRepository.save(any()) } returns mockk()
        every { gitRepositorySyncHandler.handle(any(), any()) } just Runs
        every { gitEventPublisher.publish(any<GitEvent>()) } just Runs
        every { gitIndexEvictor.evictAllOfRepository(any()) } just Runs

        // when
        handler.handle(command)

        // then
        pr.mergedBy shouldNotBe null
        pr.mergedAt shouldNotBe null
        verify(exactly = 1) { gitRepositoryPort.merge(any()) }
    }

    @Test
    fun `PR 머지 실패 - 리뷰 미완료`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val pr = PullRequestFixture.default(
            id = 1L,
            repository = repo,
            author = owner,
            statusCodeId = 1L
        )
        val command = MergePullRequestCommand(
            repositoryId = 1L,
            pullRequestId = 1L,
            requesterId = 1L,
            mergeType = "merge_commit",
            message = "머지 메시지"
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo
        every { userRepository.findByIdAndIsDeletedFalse(1L) } returns owner
        every { pullRequestRepository.findById(1L) } returns java.util.Optional.of(pr)
        every { collaboratorRepository.existsByRepositoryIdAndUserId(1L, 1L) } returns true
        every { codes.prStatusId(PrStatus.OPEN) } returns 1L
        every { reviewCommandHandler.isMergeAllowed(1L) } returns false

        // when & then
        shouldThrow<MergeNotAllowed> {
            handler.handle(command)
        }
    }
}
