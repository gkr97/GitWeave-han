package com.example.gitserver.module.pullrequest.application.query

import com.example.gitserver.fixture.PullRequestFixture
import com.example.gitserver.fixture.RepositoryFixture
import com.example.gitserver.fixture.UserFixture
import com.example.gitserver.module.common.application.service.CommonCodeCacheService
import com.example.gitserver.module.common.domain.CommonCodeDetail
import com.example.gitserver.module.common.dto.CommonCodeDetailResponse
import com.example.gitserver.module.pullrequest.domain.PullRequestReviewer
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestReviewerRepository
import io.kotest.matchers.shouldBe
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime

@ExtendWith(MockKExtension::class)
class PullRequestReviewQueryServiceTest {

    @MockK
    lateinit var reviewerRepo: PullRequestReviewerRepository

    @MockK
    lateinit var code: CommonCodeCacheService

    @InjectMockKs
    lateinit var service: PullRequestReviewQueryService

    @Test
    fun `리뷰어 목록 조회 성공`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val reviewer1 = UserFixture.default(id = 2L, name = "Reviewer1")
        val reviewer2 = UserFixture.default(id = 3L, name = "Reviewer2")
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val pr = PullRequestFixture.default(id = 1L, repository = repo, author = owner)
        
        val reviewerRow1 = PullRequestReviewer(
            id = 1L,
            pullRequest = pr,
            reviewer = reviewer1,
            statusCodeId = 1L,
            reviewedAt = null
        )
        val reviewerRow2 = PullRequestReviewer(
            id = 2L,
            pullRequest = pr,
            reviewer = reviewer2,
            statusCodeId = 2L,
            reviewedAt = LocalDateTime.now()
        )

        every { reviewerRepo.findAllByPullRequestId(1L) } returns listOf(reviewerRow1, reviewerRow2)
        every { code.getCodeDetailsOrLoad("PR_REVIEW_STATUS") } returns listOf(
            CommonCodeDetailResponse(
                id = 1L,
                code = "pending",
                name = "Pending",
                sortOrder = 1,
                isActive = true
            ),
            CommonCodeDetailResponse(
                id = 2L,
                code = "approved",
                name = "Approved",
                sortOrder = 2,
                isActive = true
            )
        )

        // when
        val result = service.listReviewers(1L)

        // then
        result.size shouldBe 2
        result[0].userId shouldBe 2L
        result[0].status shouldBe "pending"
        result[1].userId shouldBe 3L
        result[1].status shouldBe "approved"
    }

    @Test
    fun `리뷰 요약 조회 성공`() {
        // given
        every { reviewerRepo.countByStatusGrouped(1L) } returns listOf(
            arrayOf(1L, 2L), // pending: 2
            arrayOf(2L, 1L)  // approved: 1
        )
        every { code.getCodeDetailsOrLoad("PR_REVIEW_STATUS") } returns listOf(
            CommonCodeDetailResponse(
                id = 1L,
                code = "pending",
                name = "Pending",
                sortOrder = 1,
                isActive = true
            ),
            CommonCodeDetailResponse(
                id = 2L,
                code = "approved",
                name = "Approved",
                sortOrder = 2,
                isActive = true
            )
        )

        // when
        val result = service.summary(1L)

        // then
        result.total shouldBe 3
        result.pending shouldBe 2
        result.approved shouldBe 1
        result.changesRequested shouldBe 0
        result.dismissed shouldBe 0
    }
}
