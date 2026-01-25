package com.example.gitserver.module.pullrequest.application.command.handler

import com.example.gitserver.fixture.PullRequestFixture
import com.example.gitserver.fixture.RepositoryFixture
import com.example.gitserver.fixture.UserFixture
import com.example.gitserver.module.pullrequest.application.command.CreatePullRequestCommentCommand
import com.example.gitserver.module.pullrequest.application.command.DeletePullRequestCommentCommand
import com.example.gitserver.module.pullrequest.domain.PullRequest
import com.example.gitserver.module.pullrequest.domain.PullRequestComment
import com.example.gitserver.module.pullrequest.exception.PermissionDenied
import com.example.gitserver.module.pullrequest.exception.RepositoryMismatch
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestCommentRepository
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
class PullRequestCommentCommandHandlerTest {

    @MockK
    lateinit var repositoryRepository: RepositoryRepository

    @MockK
    lateinit var collaboratorRepository: CollaboratorRepository

    @MockK
    lateinit var pullRequestRepository: PullRequestRepository

    @MockK
    lateinit var userRepository: UserRepository

    @MockK
    lateinit var commentRepository: PullRequestCommentRepository

    @MockK
    lateinit var events: ApplicationEventPublisher

    @InjectMockKs
    lateinit var handler: PullRequestCommentCommandHandler

    @Test
    fun `일반 코멘트 생성 성공`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val author = UserFixture.default(id = 2L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val pr = PullRequestFixture.default(
            id = 1L,
            repository = repo,
            author = author
        )
        val command = CreatePullRequestCommentCommand(
            repositoryId = 1L,
            pullRequestId = 1L,
            authorId = 2L,
            content = "좋은 PR입니다!",
            commentType = "general",
            filePath = null,
            lineNumber = null
        )
        val savedComment = PullRequestComment(
            id = 1L,
            pullRequest = pr,
            author = author,
            content = "좋은 PR입니다!",
            filePath = null,
            lineNumber = null,
            commentType = "general"
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo
        every { pullRequestRepository.findById(1L) } returns java.util.Optional.of(pr)
        every { userRepository.findByIdAndIsDeletedFalse(2L) } returns author
        every { collaboratorRepository.existsByRepositoryIdAndUserId(1L, 2L) } returns false
        every { commentRepository.save(any()) } returns savedComment
        every { events.publishEvent(any<Any>()) } just Runs

        // when
        val result = handler.handle(command)

        // then
        result shouldBe 1L
        verify(exactly = 1) { commentRepository.save(any()) }
        verify(exactly = 1) { events.publishEvent(any<Any>()) }
    }

    @Test
    fun `인라인 코멘트 생성 성공`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val author = UserFixture.default(id = 2L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val pr = PullRequestFixture.default(
            id = 1L,
            repository = repo,
            author = author
        )
        val command = CreatePullRequestCommentCommand(
            repositoryId = 1L,
            pullRequestId = 1L,
            authorId = 2L,
            content = "이 부분 수정 필요",
            commentType = "inline",
            filePath = "src/main/Test.kt",
            lineNumber = 10
        )
        val savedComment = PullRequestComment(
            id = 1L,
            pullRequest = pr,
            author = author,
            content = "이 부분 수정 필요",
            filePath = "src/main/Test.kt",
            lineNumber = 10,
            commentType = "inline"
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo
        every { pullRequestRepository.findById(1L) } returns java.util.Optional.of(pr)
        every { userRepository.findByIdAndIsDeletedFalse(2L) } returns author
        every { collaboratorRepository.existsByRepositoryIdAndUserId(1L, 2L) } returns false
        every { commentRepository.save(any()) } returns savedComment
        every { events.publishEvent(any<Any>()) } just Runs

        // when
        val result = handler.handle(command)

        // then
        result shouldBe 1L
        verify(exactly = 1) { commentRepository.save(any()) }
    }

    @Test
    fun `코멘트 생성 실패 - 저장소 없음`() {
        // given
        val command = CreatePullRequestCommentCommand(
            repositoryId = 999L,
            pullRequestId = 1L,
            authorId = 1L,
            content = "코멘트",
            commentType = "general",
            filePath = null,
            lineNumber = null
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(999L) } returns null

        // when & then
        shouldThrow<RepositoryNotFoundException> {
            handler.handle(command)
        }
    }

    @Test
    fun `코멘트 생성 실패 - 저장소와 PR 불일치`() {
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
        val command = CreatePullRequestCommentCommand(
            repositoryId = 1L,
            pullRequestId = 1L,
            authorId = 2L,
            content = "코멘트",
            commentType = "general",
            filePath = null,
            lineNumber = null
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo1
        every { pullRequestRepository.findById(1L) } returns java.util.Optional.of(pr)

        // when & then
        shouldThrow<RepositoryMismatch> {
            handler.handle(command)
        }
    }

    @Test
    fun `코멘트 생성 실패 - 권한 없음`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val author = UserFixture.default(id = 2L)
        val unauthorized = UserFixture.default(id = 3L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val pr = PullRequestFixture.default(
            id = 1L,
            repository = repo,
            author = author
        )
        val command = CreatePullRequestCommentCommand(
            repositoryId = 1L,
            pullRequestId = 1L,
            authorId = 3L,
            content = "코멘트",
            commentType = "general",
            filePath = null,
            lineNumber = null
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo
        every { pullRequestRepository.findById(1L) } returns java.util.Optional.of(pr)
        every { userRepository.findByIdAndIsDeletedFalse(3L) } returns unauthorized
        every { collaboratorRepository.existsByRepositoryIdAndUserId(1L, 3L) } returns false

        // when & then
        shouldThrow<PermissionDenied> {
            handler.handle(command)
        }
    }

    @Test
    fun `코멘트 생성 실패 - 인라인 코멘트는 filePath 필수`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val author = UserFixture.default(id = 2L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val pr = PullRequestFixture.default(
            id = 1L,
            repository = repo,
            author = author
        )
        val command = CreatePullRequestCommentCommand(
            repositoryId = 1L,
            pullRequestId = 1L,
            authorId = 2L,
            content = "코멘트",
            commentType = "inline",
            filePath = null,
            lineNumber = null
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo
        every { pullRequestRepository.findById(1L) } returns java.util.Optional.of(pr)
        every { userRepository.findByIdAndIsDeletedFalse(2L) } returns author
        every { collaboratorRepository.existsByRepositoryIdAndUserId(1L, 2L) } returns false

        // when & then
        shouldThrow<IllegalArgumentException> {
            handler.handle(command)
        }
    }

    @Test
    fun `코멘트 삭제 성공 - 작성자 본인`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val author = UserFixture.default(id = 2L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val pr = PullRequestFixture.default(
            id = 1L,
            repository = repo,
            author = author
        )
        val comment = PullRequestComment(
            id = 1L,
            pullRequest = pr,
            author = author,
            content = "코멘트",
            commentType = "general"
        )
        val command = DeletePullRequestCommentCommand(
            repositoryId = 1L,
            pullRequestId = 1L,
            commentId = 1L,
            requesterId = 2L
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo
        every { pullRequestRepository.findById(1L) } returns java.util.Optional.of(pr)
        every { userRepository.findByIdAndIsDeletedFalse(2L) } returns author
        every { commentRepository.findById(1L) } returns java.util.Optional.of(comment)
        every { commentRepository.delete(any()) } just Runs
        every { events.publishEvent(any<Any>()) } just Runs

        // when
        handler.handle(command)

        // then
        verify(exactly = 1) { commentRepository.delete(comment) }
        verify(exactly = 1) { events.publishEvent(any<Any>()) }
    }

    @Test
    fun `코멘트 삭제 성공 - 저장소 소유자`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val author = UserFixture.default(id = 2L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val pr = PullRequestFixture.default(
            id = 1L,
            repository = repo,
            author = author
        )
        val comment = PullRequestComment(
            id = 1L,
            pullRequest = pr,
            author = author,
            content = "코멘트",
            commentType = "general"
        )
        val command = DeletePullRequestCommentCommand(
            repositoryId = 1L,
            pullRequestId = 1L,
            commentId = 1L,
            requesterId = 1L
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo
        every { pullRequestRepository.findById(1L) } returns java.util.Optional.of(pr)
        every { userRepository.findByIdAndIsDeletedFalse(1L) } returns owner
        every { commentRepository.findById(1L) } returns java.util.Optional.of(comment)
        every { commentRepository.delete(any()) } just Runs
        every { events.publishEvent(any<Any>()) } just Runs

        // when
        handler.handle(command)

        // then
        verify(exactly = 1) { commentRepository.delete(comment) }
    }

    @Test
    fun `코멘트 삭제 실패 - 권한 없음`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val author = UserFixture.default(id = 2L)
        val unauthorized = UserFixture.default(id = 3L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val pr = PullRequestFixture.default(
            id = 1L,
            repository = repo,
            author = author
        )
        val comment = PullRequestComment(
            id = 1L,
            pullRequest = pr,
            author = author,
            content = "코멘트",
            commentType = "general"
        )
        val command = DeletePullRequestCommentCommand(
            repositoryId = 1L,
            pullRequestId = 1L,
            commentId = 1L,
            requesterId = 3L
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo
        every { pullRequestRepository.findById(1L) } returns java.util.Optional.of(pr)
        every { userRepository.findByIdAndIsDeletedFalse(3L) } returns unauthorized
        every { commentRepository.findById(1L) } returns java.util.Optional.of(comment)

        // when & then
        shouldThrow<PermissionDenied> {
            handler.handle(command)
        }
    }
}
