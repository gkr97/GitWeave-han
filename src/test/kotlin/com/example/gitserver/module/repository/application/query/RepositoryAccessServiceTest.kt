package com.example.gitserver.module.repository.application.query

import com.example.gitserver.fixture.RepositoryFixture
import com.example.gitserver.fixture.UserFixture
import com.example.gitserver.module.common.application.service.CommonCodeCacheService
import com.example.gitserver.module.common.cache.RequestCache
import com.example.gitserver.module.common.dto.CommonCodeDetailResponse
import com.example.gitserver.module.repository.exception.RepositoryAccessDeniedException
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class RepositoryAccessServiceTest {

    @MockK
    lateinit var commonCodeCacheService: CommonCodeCacheService

    @MockK
    lateinit var collaboratorRepository: CollaboratorRepository

    @MockK
    lateinit var requestCache: RequestCache

    @InjectMockKs
    lateinit var service: RepositoryAccessService

    @Test
    fun `Public 저장소는 접근 허용`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val repo = RepositoryFixture.default(
            id = 1L,
            owner = owner,
            visibilityCodeId = 1L // PUBLIC
        )
        val publicCode = CommonCodeDetailResponse(
            id = 1L,
            code = "PUBLIC",
            name = "Public",
            sortOrder = 1,
            isActive = true
        )

        every { commonCodeCacheService.getCodeDetailsOrLoad("VISIBILITY") } returns listOf(publicCode)

        // when
        service.checkReadAccessOrThrow(repo, null)

        // then
        // 예외가 발생하지 않으면 성공
    }

    @Test
    fun `Private 저장소 - 소유자는 접근 허용`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val repo = RepositoryFixture.default(
            id = 1L,
            owner = owner,
            visibilityCodeId = 2L // PRIVATE
        )
        val privateCode = CommonCodeDetailResponse(
            id = 2L,
            code = "PRIVATE",
            name = "Private",
            sortOrder = 2,
            isActive = true
        )

        every { commonCodeCacheService.getCodeDetailsOrLoad("VISIBILITY") } returns listOf(privateCode)

        // when
        service.checkReadAccessOrThrow(repo, 1L)

        // then
        // 예외가 발생하지 않으면 성공
    }

    @Test
    fun `Private 저장소 - 협업자는 접근 허용`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val collaborator = UserFixture.default(id = 2L)
        val repo = RepositoryFixture.default(
            id = 1L,
            owner = owner,
            visibilityCodeId = 2L // PRIVATE
        )
        val privateCode = CommonCodeDetailResponse(
            id = 2L,
            code = "PRIVATE",
            name = "Private",
            sortOrder = 2,
            isActive = true
        )

        every { commonCodeCacheService.getCodeDetailsOrLoad("VISIBILITY") } returns listOf(privateCode)
        every { requestCache.getCollabExists(1L, 2L) } returns null
        every { collaboratorRepository.existsByRepositoryIdAndUserIdAndAcceptedTrue(1L, 2L) } returns true
        every { requestCache.putCollabExists(1L, 2L, true) } just Runs

        // when
        service.checkReadAccessOrThrow(repo, 2L)

        // then
        // 예외가 발생하지 않으면 성공
    }

    @Test
    fun `Private 저장소 - 권한 없으면 접근 거부`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val unauthorized = UserFixture.default(id = 3L)
        val repo = RepositoryFixture.default(
            id = 1L,
            owner = owner,
            visibilityCodeId = 2L // PRIVATE
        )
        val privateCode = CommonCodeDetailResponse(
            id = 2L,
            code = "PRIVATE",
            name = "Private",
            sortOrder = 2,
            isActive = true
        )

        every { commonCodeCacheService.getCodeDetailsOrLoad("VISIBILITY") } returns listOf(privateCode)
        every { requestCache.getCollabExists(1L, 3L) } returns null
        every { collaboratorRepository.existsByRepositoryIdAndUserIdAndAcceptedTrue(1L, 3L) } returns false
        every { requestCache.putCollabExists(1L, 3L, false) } just Runs

        // when & then
        shouldThrow<RepositoryAccessDeniedException> {
            service.checkReadAccessOrThrow(repo, 3L)
        }
    }

    @Test
    fun `hasReadAccess - Public 저장소는 true`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val repo = RepositoryFixture.default(
            id = 1L,
            owner = owner,
            visibilityCodeId = 1L // PUBLIC
        )
        val publicCode = CommonCodeDetailResponse(
            id = 1L,
            code = "PUBLIC",
            name = "Public",
            sortOrder = 1,
            isActive = true
        )

        every { commonCodeCacheService.getCodeDetailsOrLoad("VISIBILITY") } returns listOf(publicCode)

        // when
        val result = service.hasReadAccess(repo, null)

        // then
        result shouldBe true
    }

    @Test
    fun `isPublicRepository - Public 저장소 확인`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val repo = RepositoryFixture.default(
            id = 1L,
            owner = owner,
            visibilityCodeId = 1L // PUBLIC
        )
        val publicCode = CommonCodeDetailResponse(
            id = 1L,
            code = "PUBLIC",
            name = "Public",
            sortOrder = 1,
            isActive = true
        )

        every { commonCodeCacheService.getCodeDetailsOrLoad("VISIBILITY") } returns listOf(publicCode)

        // when
        val result = service.isPublicRepository(repo)

        // then
        result shouldBe true
    }
}
