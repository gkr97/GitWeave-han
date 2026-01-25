package com.example.gitserver.module.repository.application.query

import com.example.gitserver.fixture.RepositoryFixture
import com.example.gitserver.fixture.UserFixture
import com.example.gitserver.module.common.application.service.CommonCodeCacheService
import com.example.gitserver.module.common.dto.CommonCodeDetailResponse
import com.example.gitserver.module.repository.domain.Collaborator
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime

@ExtendWith(MockKExtension::class)
class CollaboratorQueryServiceTest {

    @MockK
    lateinit var collaboratorRepository: CollaboratorRepository

    @MockK
    lateinit var commonCodeCacheService: CommonCodeCacheService

    @InjectMockKs
    lateinit var service: CollaboratorQueryService

    @Test
    fun `협업자 목록 조회 성공 - 소유자`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val collaborator = UserFixture.default(id = 2L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val collab = Collaborator(
            id = 1L,
            repository = repo,
            user = collaborator,
            roleCodeId = 1L,
            accepted = true,
            invitedAt = LocalDateTime.now()
        )
        val roleCode = CommonCodeDetailResponse(
            id = 1L,
            code = "maintainer",
            name = "Maintainer",
            sortOrder = 1,
            isActive = true
        )

        every { collaboratorRepository.findOwnerIdByRepositoryId(1L) } returns 1L
        every { collaboratorRepository.existsByRepositoryIdAndUserId(1L, 1L) } returns false
        every { collaboratorRepository.findAllWithUserByRepositoryId(1L) } returns listOf(collab)
        every { commonCodeCacheService.getCodeDetailsOrLoad("ROLE") } returns listOf(roleCode)

        // when
        val result = service.getCollaborators(1L, 1L)

        // then
        result.size shouldBe 1
        result[0].userId shouldBe 2L
        result[0].roleCode shouldBe "maintainer"
    }

    @Test
    fun `협업자 목록 조회 성공 - 협업자 본인`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val collaborator = UserFixture.default(id = 2L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val collab = Collaborator(
            id = 1L,
            repository = repo,
            user = collaborator,
            roleCodeId = 1L,
            accepted = true
        )
        val roleCode = CommonCodeDetailResponse(
            id = 1L,
            code = "maintainer",
            name = "Maintainer",
            sortOrder = 1,
            isActive = true
        )

        every { collaboratorRepository.findOwnerIdByRepositoryId(1L) } returns 1L
        every { collaboratorRepository.existsByRepositoryIdAndUserId(1L, 2L) } returns true
        every { collaboratorRepository.findAllWithUserByRepositoryId(1L) } returns listOf(collab)
        every { commonCodeCacheService.getCodeDetailsOrLoad("ROLE") } returns listOf(roleCode)

        // when
        val result = service.getCollaborators(1L, 2L)

        // then
        result.size shouldBe 1
    }

    @Test
    fun `협업자 목록 조회 실패 - 권한 없음`() {
        // given
        every { collaboratorRepository.findOwnerIdByRepositoryId(1L) } returns 1L
        every { collaboratorRepository.existsByRepositoryIdAndUserId(1L, 3L) } returns false

        // when & then
        shouldThrow<IllegalAccessException> {
            service.getCollaborators(1L, 3L)
        }
    }
}
