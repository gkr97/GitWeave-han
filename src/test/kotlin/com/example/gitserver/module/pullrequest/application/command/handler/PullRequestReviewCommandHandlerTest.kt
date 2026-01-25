package com.example.gitserver.module.pullrequest.application.command.handler

import com.example.gitserver.fixture.PullRequestFixture
import com.example.gitserver.fixture.RepositoryFixture
import com.example.gitserver.fixture.UserFixture
import com.example.gitserver.module.pullrequest.application.RequestChangesCommand
import com.example.gitserver.module.pullrequest.application.command.*
import com.example.gitserver.module.pullrequest.domain.CodeBook
import com.example.gitserver.module.pullrequest.domain.PrReviewStatus
import com.example.gitserver.module.pullrequest.domain.PrStatus
import com.example.gitserver.module.pullrequest.domain.PullRequest
import com.example.gitserver.module.pullrequest.domain.PullRequestReviewer
import com.example.gitserver.module.pullrequest.exception.InvalidStateTransition
import com.example.gitserver.module.pullrequest.exception.NotReviewer
import com.example.gitserver.module.pullrequest.exception.PermissionDenied
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestRepository
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestReviewerRepository
import com.example.gitserver.module.repository.domain.Repository
import com.example.gitserver.module.repository.exception.RepositoryNotFoundException
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.user.domain.User
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
import org.springframework.context.ApplicationEventPublisher

@ExtendWith(MockKExtension::class)
class PullRequestReviewCommandHandlerTest {

    @MockK
    lateinit var repositoryRepository: RepositoryRepository

    @MockK
    lateinit var collaboratorRepository: CollaboratorRepository

    @MockK
    lateinit var userRepository: UserRepository

    @MockK
    lateinit var prRepository: PullRequestRepository

    @MockK
    lateinit var reviewerRepository: PullRequestReviewerRepository

    @MockK
    lateinit var codes: CodeBook

    @MockK
    lateinit var events: ApplicationEventPublisher

    @InjectMockKs
    lateinit var handler: PullRequestReviewCommandHandler

    @Test
    fun `리뷰어 지정 성공`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val reviewer = UserFixture.default(id = 2L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val pr = PullRequestFixture.default(
            id = 1L,
            repository = repo,
            author = owner,
            statusCodeId = 1L
        )
        val command = AssignReviewerCommand(
            repositoryId = 1L,
            pullRequestId = 1L,
            requesterId = 1L,
            reviewerId = 2L
        )
        val savedReviewer = PullRequestReviewer(
            id = 1L,
            pullRequest = pr,
            reviewer = reviewer,
            statusCodeId = 1L,
            reviewedAt = null
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo
        every { prRepository.findById(1L) } returns java.util.Optional.of(pr)
        every { userRepository.findByIdAndIsDeletedFalse(1L) } returns owner
        every { collaboratorRepository.existsByRepositoryIdAndUserId(1L, 1L) } returns true
        every { codes.prStatusId(PrStatus.OPEN) } returns 1L
        every { reviewerRepository.existsByPullRequestIdAndReviewerId(1L, 2L) } returns false
        every { userRepository.findByIdAndIsDeletedFalse(2L) } returns reviewer
        every { codes.prReviewStatusId(PrReviewStatus.PENDING) } returns 1L
        every { reviewerRepository.save(any()) } returns savedReviewer

        // when
        handler.handle(command)

        // then
        verify(exactly = 1) { reviewerRepository.save(any()) }
    }

    @Test
    fun `리뷰어 지정 실패 - 이미 지정된 리뷰어`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val pr = PullRequestFixture.default(
            id = 1L,
            repository = repo,
            author = owner,
            statusCodeId = 1L
        )
        val command = AssignReviewerCommand(
            repositoryId = 1L,
            pullRequestId = 1L,
            requesterId = 1L,
            reviewerId = 2L
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo
        every { prRepository.findById(1L) } returns java.util.Optional.of(pr)
        every { userRepository.findByIdAndIsDeletedFalse(1L) } returns owner
        every { collaboratorRepository.existsByRepositoryIdAndUserId(1L, 1L) } returns true
        every { codes.prStatusId(PrStatus.OPEN) } returns 1L
        every { reviewerRepository.existsByPullRequestIdAndReviewerId(1L, 2L) } returns true

        // when
        handler.handle(command)

        // then
        verify(exactly = 0) { reviewerRepository.save(any()) }
    }

    @Test
    fun `리뷰 승인 성공`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val reviewer = UserFixture.default(id = 2L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val pr = PullRequestFixture.default(
            id = 1L,
            repository = repo,
            author = owner,
            statusCodeId = 1L
        )
        val reviewerRow = PullRequestReviewer(
            id = 1L,
            pullRequest = pr,
            reviewer = reviewer,
            statusCodeId = 1L,
            reviewedAt = null
        )
        val command = ApproveReviewCommand(
            repositoryId = 1L,
            pullRequestId = 1L,
            requesterId = 2L
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo
        every { prRepository.findById(1L) } returns java.util.Optional.of(pr)
        every { userRepository.findByIdAndIsDeletedFalse(2L) } returns reviewer
        every { codes.prStatusId(PrStatus.OPEN) } returns 1L
        every { reviewerRepository.findByPullRequestIdAndReviewerId(1L, 2L) } returns reviewerRow
        every { codes.prReviewStatusId(PrReviewStatus.APPROVED) } returns 2L

        // when
        handler.handle(command)

        // then
        reviewerRow.statusCodeId shouldBe 2L
        reviewerRow.reviewedAt shouldNotBe null
    }

    @Test
    fun `리뷰 승인 실패 - 리뷰어가 아님`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val reviewer = UserFixture.default(id = 2L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val pr = PullRequestFixture.default(
            id = 1L,
            repository = repo,
            author = owner,
            statusCodeId = 1L
        )
        val command = ApproveReviewCommand(
            repositoryId = 1L,
            pullRequestId = 1L,
            requesterId = 2L
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo
        every { prRepository.findById(1L) } returns java.util.Optional.of(pr)
        every { userRepository.findByIdAndIsDeletedFalse(2L) } returns reviewer
        every { codes.prStatusId(PrStatus.OPEN) } returns 1L
        every { reviewerRepository.findByPullRequestIdAndReviewerId(1L, 2L) } returns null

        // when & then
        shouldThrow<NotReviewer> {
            handler.handle(command)
        }
    }

    @Test
    fun `변경 요청 성공`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val reviewer = UserFixture.default(id = 2L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val pr = PullRequestFixture.default(
            id = 1L,
            repository = repo,
            author = owner,
            statusCodeId = 1L
        )
        val reviewerRow = PullRequestReviewer(
            id = 1L,
            pullRequest = pr,
            reviewer = reviewer,
            statusCodeId = 1L,
            reviewedAt = null
        )
        val command = RequestChangesCommand(
            repositoryId = 1L,
            pullRequestId = 1L,
            requesterId = 2L,
            reason = "수정 필요"
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo
        every { prRepository.findById(1L) } returns java.util.Optional.of(pr)
        every { userRepository.findByIdAndIsDeletedFalse(2L) } returns reviewer
        every { codes.prStatusId(PrStatus.OPEN) } returns 1L
        every { reviewerRepository.findByPullRequestIdAndReviewerId(1L, 2L) } returns reviewerRow
        every { codes.prReviewStatusId(PrReviewStatus.CHANGES_REQUESTED) } returns 3L

        // when
        handler.handle(command)

        // then
        reviewerRow.statusCodeId shouldBe 3L
        reviewerRow.reviewedAt shouldNotBe null
    }

    @Test
    fun `머지 허용 여부 확인 - 리뷰어 없으면 허용`() {
        // given
        every { reviewerRepository.findAllByPullRequestId(1L) } returns emptyList()

        // when
        val result = handler.isMergeAllowed(1L)

        // then
        result shouldBe true
    }

    @Test
    fun `머지 허용 여부 확인 - 모두 승인하면 허용`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val reviewer1 = UserFixture.default(id = 2L)
        val reviewer2 = UserFixture.default(id = 3L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val pr = PullRequestFixture.default(id = 1L, repository = repo, author = owner)
        
        val reviewerRow1 = PullRequestReviewer(
            id = 1L,
            pullRequest = pr,
            reviewer = reviewer1,
            statusCodeId = 2L, // APPROVED
            reviewedAt = null
        )
        val reviewerRow2 = PullRequestReviewer(
            id = 2L,
            pullRequest = pr,
            reviewer = reviewer2,
            statusCodeId = 2L, // APPROVED
            reviewedAt = null
        )

        every { reviewerRepository.findAllByPullRequestId(1L) } returns listOf(reviewerRow1, reviewerRow2)
        every { codes.prReviewStatusId(PrReviewStatus.APPROVED) } returns 2L
        every { codes.prReviewStatusId(PrReviewStatus.CHANGES_REQUESTED) } returns 3L

        // when
        val result = handler.isMergeAllowed(1L)

        // then
        result shouldBe true
    }

    @Test
    fun `머지 허용 여부 확인 - 변경 요청이 있으면 불허`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val reviewer1 = UserFixture.default(id = 2L)
        val reviewer2 = UserFixture.default(id = 3L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val pr = PullRequestFixture.default(id = 1L, repository = repo, author = owner)
        
        val reviewerRow1 = PullRequestReviewer(
            id = 1L,
            pullRequest = pr,
            reviewer = reviewer1,
            statusCodeId = 2L, // APPROVED
            reviewedAt = null
        )
        val reviewerRow2 = PullRequestReviewer(
            id = 2L,
            pullRequest = pr,
            reviewer = reviewer2,
            statusCodeId = 3L, // CHANGES_REQUESTED
            reviewedAt = null
        )

        every { reviewerRepository.findAllByPullRequestId(1L) } returns listOf(reviewerRow1, reviewerRow2)
        every { codes.prReviewStatusId(PrReviewStatus.APPROVED) } returns 2L
        every { codes.prReviewStatusId(PrReviewStatus.CHANGES_REQUESTED) } returns 3L

        // when
        val result = handler.isMergeAllowed(1L)

        // then
        result shouldBe false
    }
}
