package com.example.gitserver.module.pullrequest.application.query

import com.example.gitserver.fixture.PullRequestFixture
import com.example.gitserver.fixture.RepositoryFixture
import com.example.gitserver.fixture.UserFixture
import com.example.gitserver.module.pullrequest.domain.PullRequestComment
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestCommentRepository
import io.kotest.matchers.shouldBe
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class PullRequestCommentQueryServiceTest {

    @MockK
    lateinit var commentRepository: PullRequestCommentRepository

    @InjectMockKs
    lateinit var service: PullRequestCommentQueryService

    @Test
    fun `코멘트 목록 조회 성공`() {
        // given
        val owner = UserFixture.default(id = 1L)
        val author = UserFixture.default(id = 2L)
        val repo = RepositoryFixture.default(id = 1L, owner = owner)
        val pr = PullRequestFixture.default(id = 1L, repository = repo, author = author)
        
        val comment1 = PullRequestComment(
            id = 1L,
            pullRequest = pr,
            author = author,
            content = "첫 번째 코멘트",
            commentType = "general"
        )
        val comment2 = PullRequestComment(
            id = 2L,
            pullRequest = pr,
            author = author,
            content = "두 번째 코멘트",
            commentType = "inline",
            filePath = "src/Test.kt",
            lineNumber = 10
        )

        every { commentRepository.findAllByPullRequestIdOrderByCreated(1L) } returns listOf(comment1, comment2)

        // when
        val result = service.list(1L)

        // then
        result.size shouldBe 2
        result[0].content shouldBe "첫 번째 코멘트"
        result[1].content shouldBe "두 번째 코멘트"
        result[1].filePath shouldBe "src/Test.kt"
        result[1].lineNumber shouldBe 10
    }

    @Test
    fun `코멘트 목록 조회 - 코멘트 없음`() {
        // given
        every { commentRepository.findAllByPullRequestIdOrderByCreated(1L) } returns emptyList()

        // when
        val result = service.list(1L)

        // then
        result.size shouldBe 0
    }
}
