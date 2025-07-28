package com.example.gitserver.module.repository.application.command.handler

import com.example.gitserver.fixture.UserFixture
import com.example.gitserver.module.common.dto.CommonCodeDetailResponse
import com.example.gitserver.module.common.service.CommonCodeCacheService
import com.example.gitserver.module.repository.application.command.CreateRepositoryCommand
import com.example.gitserver.module.repository.application.service.GitService
import com.example.gitserver.module.repository.domain.event.RepositoryEventPublisher
import com.example.gitserver.module.repository.exception.*
import com.example.gitserver.module.repository.infrastructure.persistence.BranchRepository
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.*

class CreateRepositoryCommandHandlerTest {

    private lateinit var handler: CreateRepositoryCommandHandler

    private val repositoryRepository = mock<RepositoryRepository>()
    private val branchRepository = mock<BranchRepository>()
    private val gitService = mock<GitService>()
    private val repositoryEventPublisher = mock<RepositoryEventPublisher>()
    private val commonCodeCacheService = mock<CommonCodeCacheService>()
    private val collaboratorRepository = mock<CollaboratorRepository>()
    private val userRepository = mock<UserRepository>()

    private val owner = UserFixture.default(id = 1L)
    private val invitee = UserFixture.default(id = 2L)

    @BeforeEach
    fun setUp() {
        handler = CreateRepositoryCommandHandler(
            repositoryRepository,
            branchRepository,
            gitService,
            repositoryEventPublisher,
            commonCodeCacheService,
            collaboratorRepository,
            userRepository
        )
        TransactionSynchronizationManager.initSynchronization()
    }

    @AfterEach
    fun tearDown() {
        TransactionSynchronizationManager.clearSynchronization()
    }

    private fun defaultCommand(
        name: String = "test-repo",
        visibility: String? = "PUBLIC",
        invitees: List<Long>? = null
    ) = CreateRepositoryCommand(
        owner = owner,
        name = name,
        description = "설명",
        visibilityCode = visibility,
        license = "MIT",
        language = "Kotlin",
        homepageUrl = "https://example.com",
        topics = "git,repo",
        invitedUserIds = invitees
    )

    @Test
    fun `저장소 생성`() {
        val command = defaultCommand(invitees = listOf(2L))

        whenever(repositoryRepository.existsByOwnerIdAndName(1L, "test-repo")).thenReturn(false)
        whenever(commonCodeCacheService.getCodeDetailsOrLoad("VISIBILITY"))
            .thenReturn(listOf(CommonCodeDetailResponse(1L, "PUBLIC", "공개", 0, true)))
        whenever(commonCodeCacheService.getCodeDetailsOrLoad("ROLE"))
            .thenReturn(listOf(
                CommonCodeDetailResponse(1L, "owner", "소유자", 0, true),
                CommonCodeDetailResponse(2L, "maintainer", "메인터이너", 1, true)
            ))
        whenever(userRepository.findById(2L)).thenReturn(Optional.of(invitee))
        whenever(gitService.getHeadCommitHash(any(), eq("main"))).thenReturn("abc123")

        val result = handler.handle(command)

        assertThat(result.name).isEqualTo("test-repo")
        verify(repositoryRepository).saveAndFlush(any())
        verify(branchRepository).save(any())
        verify(collaboratorRepository, times(2)).save(any()) // owner + invitee

        TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
        verify(repositoryEventPublisher).publishRepositoryCreatedEvent(any())
    }

    @Test
    fun `중복 저장소명 예외`() {
        whenever(repositoryRepository.existsByOwnerIdAndName(1L, "duplicate")).thenReturn(true)

        val command = defaultCommand(name = "duplicate")

        assertThrows<DuplicateRepositoryNameException> {
            handler.handle(command)
        }
    }

    @Test
    fun `잘못된 코드 예외`() {
        whenever(repositoryRepository.existsByOwnerIdAndName(any(), any())).thenReturn(false)
        whenever(commonCodeCacheService.getCodeDetailsOrLoad("VISIBILITY")).thenReturn(emptyList())

        val command = defaultCommand(visibility = "INVALID")

        assertThrows<InvalidVisibilityCodeException> {
            handler.handle(command)
        }
    }

    @Test
    fun `헤드 커밋이 없을 경우 예외`() {
        whenever(repositoryRepository.existsByOwnerIdAndName(any(), any())).thenReturn(false)
        whenever(commonCodeCacheService.getCodeDetailsOrLoad("VISIBILITY"))
            .thenReturn(listOf(CommonCodeDetailResponse(1L, "PUBLIC", "공개", 0, true)))
        whenever(commonCodeCacheService.getCodeDetailsOrLoad("ROLE"))
            .thenReturn(listOf(CommonCodeDetailResponse(1L, "owner", "소유자", 0, true)))
        whenever(gitService.getHeadCommitHash(any(), any())).thenReturn(null)

        val command = defaultCommand()

        assertThrows<HeadCommitNotFoundException> {
            handler.handle(command)
        }
    }

    @Test
    fun `초대 유저가 존재하지 않으면 예외`() {
        whenever(repositoryRepository.existsByOwnerIdAndName(any(), any())).thenReturn(false)
        whenever(gitService.getHeadCommitHash(any(), any())).thenReturn("dummy-head")
        whenever(commonCodeCacheService.getCodeDetailsOrLoad("VISIBILITY"))
            .thenReturn(listOf(CommonCodeDetailResponse(1L, "PUBLIC", "공개", 0, true)))
        whenever(commonCodeCacheService.getCodeDetailsOrLoad("ROLE"))
            .thenReturn(listOf(
                CommonCodeDetailResponse(1L, "owner", "소유자", 0, true),
                CommonCodeDetailResponse(2L, "maintainer", "유지보수자", 1, true)
            ))
        whenever(userRepository.findById(999L)).thenReturn(Optional.empty())

        val command = defaultCommand(invitees = listOf(999L))

        assertThrows<UserNotFoundException> {
            handler.handle(command)
        }
    }
}
