package com.example.gitserver.module.repository.application.query

import com.example.gitserver.common.pagination.KeysetPaging
import com.example.gitserver.common.pagination.SortDirection
import com.example.gitserver.fixture.RepositoryFixture
import com.example.gitserver.fixture.UserFixture
import com.example.gitserver.module.repository.application.query.model.BranchKeysetReq
import com.example.gitserver.module.repository.application.query.model.BranchRow
import com.example.gitserver.module.repository.application.query.model.BranchSortBy
import com.example.gitserver.module.repository.domain.Repository
import com.example.gitserver.module.repository.exception.*
import com.example.gitserver.module.repository.infrastructure.persistence.BranchKeysetRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime

@ExtendWith(MockKExtension::class)
class BranchQueryServiceTest {

    @MockK
    lateinit var repositoryRepository: RepositoryRepository

    @MockK
    lateinit var branchKeysetRepository: BranchKeysetRepository

    @MockK
    lateinit var repositoryAccessService: RepositoryAccessService

    @InjectMockKs
    lateinit var service: BranchQueryService

    @Test
    fun `브랜치 목록 조회 성공`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val paging = KeysetPaging(first = 10)
        val rows = listOf(
            BranchRow(
                id = 1L,
                repositoryId = 1L,
                name = "refs/heads/main",
                isDefault = true,
                isProtected = false,
                createdAt = LocalDateTime.now(),
                headCommitHash = "abc123",
                lastCommitAt = LocalDateTime.now(),
                creatorId = 1L,
                creatorNickname = "Owner",
                creatorProfileImageUrl = null,
            )
        )

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo
        every { repositoryAccessService.checkReadAccessOrThrow(any(), any()) } just Runs
        every { branchKeysetRepository.query(any()) } returns rows

        // when
        val result = service.getBranchConnection(
            repositoryId = 1L,
            paging = paging,
            sortBy = "LAST_COMMIT_AT",
            sortDirection = "DESC",
            keyword = null,
            onlyMine = false,
            currentUserId = null
        )

        // then
        result.edges.size shouldBe 1
        result.pageInfo shouldNotBe null
    }

    @Test
    fun `브랜치 목록 조회 실패 - 저장소 없음`() {
        // given
        val paging = KeysetPaging(first = 10)

        every { repositoryRepository.findByIdAndIsDeletedFalse(999L) } returns null

        // when & then
        shouldThrow<RepositoryNotFoundException> {
            service.getBranchConnection(
                repositoryId = 999L,
                paging = paging,
                sortBy = "LAST_COMMIT_AT",
                sortDirection = "DESC",
                keyword = null,
                onlyMine = false,
                currentUserId = null
            )
        }
    }

    @Test
    fun `브랜치 목록 조회 실패 - 권한 없음`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val paging = KeysetPaging(first = 10)

        every { repositoryRepository.findByIdAndIsDeletedFalse(1L) } returns repo
        every { repositoryAccessService.checkReadAccessOrThrow(any(), any()) } throws RepositoryAccessDeniedException(1L, 2L)

        // when & then
        shouldThrow<RepositoryAccessDeniedException> {
            service.getBranchConnection(
                repositoryId = 1L,
                paging = paging,
                sortBy = "LAST_COMMIT_AT",
                sortDirection = "DESC",
                keyword = null,
                onlyMine = false,
                currentUserId = 2L
            )
        }
    }
}
