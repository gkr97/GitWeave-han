package com.example.gitserver.module.repository.application.command.handler

import com.example.gitserver.fixture.RepositoryFixture
import com.example.gitserver.fixture.UserFixture
import com.example.gitserver.module.common.application.service.CommonCodeCacheService
import com.example.gitserver.module.common.cache.RepoCacheEvictor
import com.example.gitserver.module.common.domain.CommonCodeDetail
import com.example.gitserver.module.common.dto.CommonCodeDetailResponse
import com.example.gitserver.module.repository.domain.Collaborator
import com.example.gitserver.module.repository.domain.Repository
import com.example.gitserver.module.repository.exception.*
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
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
import java.time.LocalDateTime

@ExtendWith(MockKExtension::class)
class CollaboratorCommandHandlerTest {

    @MockK
    lateinit var collaboratorRepository: CollaboratorRepository

    @MockK
    lateinit var repositoryRepository: RepositoryRepository

    @MockK
    lateinit var userRepository: UserRepository

    @MockK
    lateinit var commonCodeCacheService: CommonCodeCacheService

    @MockK
    lateinit var evictor: RepoCacheEvictor

    @MockK
    lateinit var access: com.example.gitserver.module.repository.domain.policy.RepoAccessPolicy

    @MockK
    lateinit var events: ApplicationEventPublisher

    @InjectMockKs
    lateinit var handler: CollaboratorCommandHandler

    @Test
    fun `협업자 초대 성공`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val invitee = UserFixture.default(id = 2L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val roleCode = CommonCodeDetailResponse(
            id = 1L,
            code = "maintainer",
            name = "Maintainer",
            sortOrder = 1,
            isActive = true
        )
        val savedCollaborator = Collaborator(
            id = 1L,
            repository = repo,
            user = invitee,
            roleCodeId = 1L,
            accepted = false,
            invitedAt = LocalDateTime.now()
        )

        every { repositoryRepository.findById(1L) } returns java.util.Optional.of(repo)
        every { access.isOwner(1L, 1L) } returns true
        every { collaboratorRepository.findByRepositoryIdAndUserId(1L, 2L) } returns null
        every { userRepository.findById(2L) } returns java.util.Optional.of(invitee)
        every { commonCodeCacheService.getCodeDetailsOrLoad("ROLE") } returns listOf(roleCode)
        every { collaboratorRepository.save(any()) } returns savedCollaborator
        every { events.publishEvent(any<Any>()) } just Runs

        // when
        handler.inviteCollaborator(1L, 2L, 1L)

        // then
        verify(exactly = 1) { collaboratorRepository.save(any()) }
        verify(exactly = 1) { events.publishEvent(any<Any>()) }
    }

    @Test
    fun `협업자 초대 실패 - 소유자가 아님`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)

        every { repositoryRepository.findById(1L) } returns java.util.Optional.of(repo)
        every { access.isOwner(1L, 2L) } returns false

        // when & then
        shouldThrow<NotRepositoryOwnerException> {
            handler.inviteCollaborator(1L, 3L, 2L)
        }
    }

    @Test
    fun `협업자 초대 실패 - 이미 초대됨`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val invitee = UserFixture.default(id = 2L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val existing = Collaborator(
            id = 1L,
            repository = repo,
            user = invitee,
            roleCodeId = 1L,
            accepted = false
        )

        every { repositoryRepository.findById(1L) } returns java.util.Optional.of(repo)
        every { access.isOwner(1L, 1L) } returns true
        every { collaboratorRepository.findByRepositoryIdAndUserId(1L, 2L) } returns existing

        // when & then
        shouldThrow<CollaboratorAlreadyInvitedException> {
            handler.inviteCollaborator(1L, 2L, 1L)
        }
    }

    @Test
    fun `협업자 초대 수락 성공`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val collaborator = UserFixture.default(id = 2L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val collab = Collaborator(
            id = 1L,
            repository = repo,
            user = collaborator,
            roleCodeId = 1L,
            accepted = false
        )

        every { collaboratorRepository.findByRepositoryIdAndUserId(1L, 2L) } returns collab
        every { events.publishEvent(any<Any>()) } just Runs

        // when
        handler.acceptInvitation(1L, 2L)

        // then
        collab.accepted shouldBe true
        verify(exactly = 1) { events.publishEvent(any<Any>()) }
    }

    @Test
    fun `협업자 초대 수락 실패 - 협업자 없음`() {
        // given
        every { collaboratorRepository.findByRepositoryIdAndUserId(1L, 2L) } returns null

        // when & then
        shouldThrow<CollaboratorNotFoundException> {
            handler.acceptInvitation(1L, 2L)
        }
    }

    @Test
    fun `협업자 초대 거절 성공`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val collaborator = UserFixture.default(id = 2L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val collab = Collaborator(
            id = 1L,
            repository = repo,
            user = collaborator,
            roleCodeId = 1L,
            accepted = false
        )

        every { collaboratorRepository.findByRepositoryIdAndUserId(1L, 2L) } returns collab
        every { collaboratorRepository.delete(any()) } just Runs
        every { events.publishEvent(any<Any>()) } just Runs

        // when
        handler.rejectInvitation(1L, 2L)

        // then
        verify(exactly = 1) { collaboratorRepository.delete(collab) }
        verify(exactly = 1) { events.publishEvent(any<Any>()) }
    }

    @Test
    fun `협업자 제거 성공`() {
        // given
        every { access.isOwner(1L, 1L) } returns true
        every { collaboratorRepository.existsByRepositoryIdAndUserId(1L, 2L) } returns true
        every { collaboratorRepository.deleteByRepositoryIdAndUserId(1L, 2L) } just Runs
        every { events.publishEvent(any<Any>()) } just Runs

        // when
        handler.removeCollaborator(1L, 2L, 1L)

        // then
        verify(exactly = 1) { collaboratorRepository.deleteByRepositoryIdAndUserId(1L, 2L) }
        verify(exactly = 1) { events.publishEvent(any<Any>()) }
    }

    @Test
    fun `협업자 제거 실패 - 소유자가 아님`() {
        // given
        every { access.isOwner(1L, 2L) } returns false

        // when & then
        shouldThrow<NotRepositoryOwnerException> {
            handler.removeCollaborator(1L, 3L, 2L)
        }
    }
}
