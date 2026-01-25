package com.example.gitserver.module.pullrequest.application.query

import com.example.gitserver.module.pullrequest.application.query.model.PullRequestDetail
import com.example.gitserver.module.pullrequest.application.query.model.PullRequestListItem
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestJdbcRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class PullRequestQueryServiceTest {

    @MockK
    lateinit var repoRepo: RepositoryRepository

    @MockK
    lateinit var prJdbc: PullRequestJdbcRepository

    @InjectMockKs
    lateinit var service: PullRequestQueryService

    @Test
    fun `PR 목록 조회 성공`() {
        // given
        val items = listOf(
            PullRequestListItem(
                id = 1L,
                repositoryId = 1L,
                title = "PR 1",
                status = "OPEN",
                sourceBranch = "feature",
                targetBranch = "main",
                author = mockk(),
                createdAt = java.time.LocalDateTime.now(),
                updatedAt = null
            )
        )

        every { repoRepo.existsById(1L) } returns true
        every { prJdbc.queryList(1L, null, null, "updatedAt", "DESC", 0, 20) } returns (items to 1)

        // when
        val (content, total) = service.getList(1L, null, null, null, null, null, null)

        // then
        content.size shouldBe 1
        total shouldBe 1
    }

    @Test
    fun `PR 목록 조회 - 저장소 없으면 빈 목록`() {
        // given
        every { repoRepo.existsById(999L) } returns false

        // when
        val (content, total) = service.getList(999L, null, null, null, null, null, null)

        // then
        content.size shouldBe 0
        total shouldBe 0
    }

    @Test
    fun `PR 상세 조회 성공`() {
        // given
        val detail = PullRequestDetail(
            id = 1L,
            repositoryId = 1L,
            title = "PR 1",
            description = "설명",
            status = "OPEN",
            sourceBranch = "feature",
            targetBranch = "main",
            baseCommitHash = "abc123",
            headCommitHash = "def456",
            author = mockk(),
            createdAt = java.time.LocalDateTime.now(),
            updatedAt = null
        )

        every { repoRepo.existsById(1L) } returns true
        every { prJdbc.queryDetail(1L, 1L) } returns detail

        // when
        val result = service.getDetail(1L, 1L)

        // then
        result shouldNotBe null
        result?.id shouldBe 1L
    }

    @Test
    fun `PR 상세 조회 - 저장소 없으면 null`() {
        // given
        every { repoRepo.existsById(999L) } returns false

        // when
        val result = service.getDetail(999L, 1L)

        // then
        result shouldBe null
    }
}
