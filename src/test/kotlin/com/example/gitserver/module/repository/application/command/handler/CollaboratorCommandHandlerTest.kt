package com.example.gitserver.module.repository.application.command.handler

import com.example.gitserver.fixture.UserFixture
import com.example.gitserver.module.common.dto.CommonCodeDetailResponse
import com.example.gitserver.module.common.service.CommonCodeCacheService
import com.example.gitserver.module.repository.domain.Collaborator
import com.example.gitserver.module.repository.domain.Repository
import com.example.gitserver.module.repository.exception.*
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.time.LocalDateTime
import java.util.*

class CollaboratorCommandHandlerTest {

    private lateinit var handler: CollaboratorCommandHandler
    private val collaboratorRepository: CollaboratorRepository = mock()
    private val repositoryRepository: RepositoryRepository = mock()
    private val userRepository: UserRepository = mock()
    private val commonCodeCacheService: CommonCodeCacheService = mock()

    private val owner = UserFixture.default(id = 1L)
    private val invitee = UserFixture.default(id = 2L)
    private val repository = Repository(
        id = 100L,
        name = "repo-name",
        owner = owner,
        visibilityCodeId = 1L,
        defaultBranch = "main"
    )

    @BeforeEach
    fun setUp() {
        handler = CollaboratorCommandHandler(
            collaboratorRepository,
            repositoryRepository,
            userRepository,
            commonCodeCacheService
        )
    }

    @Test
    fun `정상 초대`() {
        whenever(repositoryRepository.findById(100L)).thenReturn(Optional.of(repository))
        whenever(collaboratorRepository.existsByRepositoryIdAndUserId(100L, 2L)).thenReturn(false)
        whenever(collaboratorRepository.findByRepositoryIdAndUserId(100L, 2L)).thenReturn(null)
        whenever(userRepository.findById(2L)).thenReturn(Optional.of(invitee))

        val roleCodes = listOf(
            CommonCodeDetailResponse(
                id = 1L,
                code = "owner",
                name = "소유자",
                sortOrder = 0,
                isActive = true
            ),
            CommonCodeDetailResponse(
                id = 10L,
                code = "maintainer",
                name = "Maintainer",
                sortOrder = 1,
                isActive = true
            )
        )

        whenever(commonCodeCacheService.getCodeDetailsOrLoad("ROLE"))
            .thenReturn(roleCodes)

        handler.inviteCollaborator(100L, 2L, 1L)

        verify(collaboratorRepository).save(argThat {
            this.repository.id == 100L &&
                    this.user.id == 2L &&
                    this.roleCodeId == 10L &&
                    !this.accepted &&
                    this.invitedAt is LocalDateTime
        })
    }

    @Test
    fun `소유자 아니면 초대 실패`() {
        whenever(repositoryRepository.findById(100L)).thenReturn(Optional.of(repository))

        assertThrows<NotRepositoryOwnerException> {
            handler.inviteCollaborator(100L, 2L, requesterId = 99L)
        }
    }

    @Test
    fun `이미 등록된 collaborator이면 실패`() {
        whenever(repositoryRepository.findById(100L)).thenReturn(Optional.of(repository))
        whenever(collaboratorRepository.existsByRepositoryIdAndUserId(100L, 2L)).thenReturn(true)

        assertThrows<CollaboratorAlreadyExistsException> {
            handler.inviteCollaborator(100L, 2L, requesterId = 1L)
        }
    }

    @Test
    fun `이미 초대한 사용자이면 실패`() {
        val existing = Collaborator(
            repository = repository,
            user = invitee,
            roleCodeId = 10L,
            invitedAt = LocalDateTime.now(),
            accepted = false
        )
        whenever(repositoryRepository.findById(100L)).thenReturn(Optional.of(repository))
        whenever(collaboratorRepository.existsByRepositoryIdAndUserId(100L, 2L)).thenReturn(false)
        whenever(collaboratorRepository.findByRepositoryIdAndUserId(100L, 2L)).thenReturn(existing)

        assertThrows<CollaboratorAlreadyInvitedException> {
            handler.inviteCollaborator(100L, 2L, 1L)
        }
    }

    @Test
    fun `초대 수락 시 accepted true로 변경`() {
        val collaborator = Collaborator(
            repository = repository,
            user = invitee,
            roleCodeId = 10L,
            invitedAt = LocalDateTime.now(),
            accepted = false
        )
        whenever(collaboratorRepository.findByRepositoryIdAndUserId(100L, 2L)).thenReturn(collaborator)

        handler.acceptInvitation(100L, 2L)

        assertThat(collaborator.accepted).isTrue()
    }

    @Test
    fun `초대 거절 시 collaborator 삭제`() {
        val collaborator = Collaborator(
            repository = repository,
            user = invitee,
            roleCodeId = 10L,
            invitedAt = LocalDateTime.now(),
            accepted = false
        )
        whenever(collaboratorRepository.findByRepositoryIdAndUserId(100L, 2L)).thenReturn(collaborator)

        handler.rejectInvitation(100L, 2L)

        verify(collaboratorRepository).delete(collaborator)
    }

    @Test
    fun `강제 삭제 시 정상 동작`() {
        whenever(repositoryRepository.findById(100L)).thenReturn(Optional.of(repository))
        whenever(collaboratorRepository.existsByRepositoryIdAndUserId(100L, 2L)).thenReturn(true)

        handler.removeCollaborator(100L, 2L, requesterId = 1L)

        verify(collaboratorRepository).deleteByRepositoryIdAndUserId(100L, 2L)
    }
}
