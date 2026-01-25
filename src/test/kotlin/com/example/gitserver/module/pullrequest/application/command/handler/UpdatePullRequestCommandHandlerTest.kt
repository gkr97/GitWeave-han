package com.example.gitserver.module.pullrequest.application.command.handler

import com.example.gitserver.fixture.PullRequestFixture
import com.example.gitserver.fixture.RepositoryFixture
import com.example.gitserver.fixture.UserFixture
import com.example.gitserver.module.pullrequest.application.command.UpdatePullRequestCommand
import com.example.gitserver.module.pullrequest.domain.CodeBook
import com.example.gitserver.module.pullrequest.domain.PrStatus
import com.example.gitserver.module.pullrequest.exception.InvalidStateTransition
import com.example.gitserver.module.pullrequest.exception.PermissionDenied
import com.example.gitserver.module.pullrequest.exception.RepositoryMismatch
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestRepository
import com.example.gitserver.module.repository.domain.Repository
import com.example.gitserver.module.repository.exception.RepositoryNotFoundException
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.user.domain.User
import com.example.gitserver.module.user.exception.UserNotFoundException
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
class UpdatePullRequestCommandHandlerTest {

    @MockK
    lateinit var repositoryRepository: RepositoryRepository

    @MockK
    lateinit var collaboratorRepository: CollaboratorRepository

    @MockK
    lateinit var userRepository: UserRepository

    @MockK
    lateinit var pullRequestRepository: PullRequestRepository

    @MockK
    lateinit var codes: CodeBook

    @MockK
    lateinit var events: ApplicationEventPublisher

    @InjectMockKs
    lateinit var handler: UpdatePullRequestCommandHandler

    @Test
    fun `PR 수정 성공 - 제목만 변경`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val author = UserFixture.default(id = 2L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val pr = PullRequestFixture.default(
            id = 1L,
            repository = repo,
            author = author,
            statusCodeId = 1L
        )
        val command = UpdatePullRequestCommand(
            repositoryId = 1L,
            pullRequestId = 1L,
            requesterId = 2L,
            title = "수정된 제목",
            description = null
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo
        every { userRepository.findByIdAndIsDeletedFalse(2L) } returns author
        every { pullRequestRepository.findById(1L) } returns java.util.Optional.of(pr)
        every { collaboratorRepository.existsByRepositoryIdAndUserId(1L, 2L) } returns false
        every { codes.prStatusId(PrStatus.OPEN) } returns 1L
        every { pullRequestRepository.save(any()) } returns pr

        // when
        handler.handle(command)

        // then
        pr.title shouldBe "수정된 제목"
        verify(exactly = 1) { pullRequestRepository.save(pr) }
    }

    @Test
    fun `PR 수정 성공 - 설명만 변경`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val author = UserFixture.default(id = 2L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val pr = PullRequestFixture.default(
            id = 1L,
            repository = repo,
            author = author,
            statusCodeId = 1L
        )
        val command = UpdatePullRequestCommand(
            repositoryId = 1L,
            pullRequestId = 1L,
            requesterId = 2L,
            title = null,
            description = "수정된 설명"
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo
        every { userRepository.findByIdAndIsDeletedFalse(2L) } returns author
        every { pullRequestRepository.findById(1L) } returns java.util.Optional.of(pr)
        every { collaboratorRepository.existsByRepositoryIdAndUserId(1L, 2L) } returns false
        every { codes.prStatusId(PrStatus.OPEN) } returns 1L
        every { pullRequestRepository.save(any()) } returns pr

        // when
        handler.handle(command)

        // then
        pr.description shouldBe "수정된 설명"
        verify(exactly = 1) { pullRequestRepository.save(pr) }
    }

    @Test
    fun `PR 수정 실패 - 저장소 없음`() {
        // given
        val command = UpdatePullRequestCommand(
            repositoryId = 999L,
            pullRequestId = 1L,
            requesterId = 1L,
            title = "제목",
            description = null
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(999L) } returns null

        // when & then
        shouldThrow<RepositoryNotFoundException> {
            handler.handle(command)
        }
    }

    @Test
    fun `PR 수정 실패 - 저장소와 PR 불일치`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val author = UserFixture.default(id = 2L)
        val repo1 = RepositoryFixture.default(id = 1L, owner = owner)
        val repo2 = RepositoryFixture.default(id = 2L, owner = owner)
        val pr = PullRequestFixture.default(
            id = 1L,
            repository = repo2,
            author = author
        )
        val command = UpdatePullRequestCommand(
            repositoryId = 1L,
            pullRequestId = 1L,
            requesterId = 2L,
            title = "제목",
            description = null
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo1
        every { userRepository.findByIdAndIsDeletedFalse(2L) } returns author
        every { pullRequestRepository.findById(1L) } returns java.util.Optional.of(pr)

        // when & then
        shouldThrow<RepositoryMismatch> {
            handler.handle(command)
        }
    }

    @Test
    fun `PR 수정 실패 - 권한 없음`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val author = UserFixture.default(id = 2L)
        val requester = UserFixture.default(id = 3L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val pr = PullRequestFixture.default(
            id = 1L,
            repository = repo,
            author = author,
            statusCodeId = 1L
        )
        val command = UpdatePullRequestCommand(
            repositoryId = 1L,
            pullRequestId = 1L,
            requesterId = 3L,
            title = "제목",
            description = null
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo
        every { userRepository.findByIdAndIsDeletedFalse(3L) } returns requester
        every { pullRequestRepository.findById(1L) } returns java.util.Optional.of(pr)
        every { collaboratorRepository.existsByRepositoryIdAndUserId(1L, 3L) } returns false

        // when & then
        shouldThrow<PermissionDenied> {
            handler.handle(command)
        }
    }

    @Test
    fun `PR 수정 실패 - 닫힌 PR은 수정 불가`() {
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
        val command = UpdatePullRequestCommand(
            repositoryId = 1L,
            pullRequestId = 1L,
            requesterId = 2L,
            title = "제목",
            description = null
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo
        every { userRepository.findByIdAndIsDeletedFalse(2L) } returns author
        every { pullRequestRepository.findById(1L) } returns java.util.Optional.of(pr)
        every { collaboratorRepository.existsByRepositoryIdAndUserId(1L, 2L) } returns false
        every { codes.prStatusId(PrStatus.OPEN) } returns 1L

        // when & then
        shouldThrow<InvalidStateTransition> {
            handler.handle(command)
        }
    }

    @Test
    fun `PR 수정 - 변경사항 없으면 저장 안함`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val author = UserFixture.default(id = 2L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val pr = PullRequestFixture.default(
            id = 1L,
            repository = repo,
            author = author,
            statusCodeId = 1L
        )
        val command = UpdatePullRequestCommand(
            repositoryId = 1L,
            pullRequestId = 1L,
            requesterId = 2L,
            title = null,
            description = null
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo
        every { userRepository.findByIdAndIsDeletedFalse(2L) } returns author
        every { pullRequestRepository.findById(1L) } returns java.util.Optional.of(pr)
        every { collaboratorRepository.existsByRepositoryIdAndUserId(1L, 2L) } returns false
        every { codes.prStatusId(PrStatus.OPEN) } returns 1L

        // when
        handler.handle(command)

        // then
        verify(exactly = 0) { pullRequestRepository.save(any()) }
    }
}
