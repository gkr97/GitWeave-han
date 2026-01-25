package com.example.gitserver.module.gitindex.application.query

import com.example.gitserver.common.util.GitRefUtils
import com.example.gitserver.module.gitindex.domain.port.CommitQueryRepository
import com.example.gitserver.module.gitindex.infrastructure.redis.GitIndexCache
import com.example.gitserver.module.repository.interfaces.dto.CommitResponse
import com.example.gitserver.module.repository.interfaces.dto.TreeNodeResponse
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Instant
import java.time.ZoneOffset

@ExtendWith(MockKExtension::class)
class CommitQueryServiceTest {

    @MockK
    lateinit var commitQueryRepository: CommitQueryRepository

    @MockK
    lateinit var cache: GitIndexCache

    @InjectMockKs
    lateinit var service: CommitQueryService

    @Test
    fun `최신 커밋 해시 조회 성공`() {
        // given
        val commit = CommitResponse(
            hash = "abc123",
            message = "커밋 메시지",
            committedAt = Instant.now().atOffset(ZoneOffset.UTC).toLocalDateTime(),
            author = mockk()
        )

        every { commitQueryRepository.getLatestCommit(1L, GitRefUtils.toFullRef("main")) } returns commit

        // when
        val result = service.getLatestCommitHash(1L, "main")

        // then
        result shouldNotBe null
        result?.hash shouldBe "abc123"
    }

    @Test
    fun `커밋 정보 조회 성공`() {
        // given
        val commit = CommitResponse(
            hash = "abc123",
            message = "커밋 메시지",
            committedAt = Instant.now().atOffset(ZoneOffset.UTC).toLocalDateTime(),
            author = mockk()
        )

        every { cache.getCommitByHash(1L, "abc123") } returns commit

        // when
        val result = service.getCommitInfo(1L, "abc123")

        // then
        result shouldNotBe null
        result?.hash shouldBe "abc123"
    }

    @Test
    fun `파일 트리 조회 성공`() {
        // given
        val treeNodes = listOf(
            TreeNodeResponse(
                name = "file.txt",
                path = "file.txt",
                isDirectory = false,
                size = 100L,
                lastCommitHash = "abc123",
                lastCommitMessage = "커밋",
                lastCommittedAt = Instant.now().atOffset(ZoneOffset.UTC).toLocalDateTime().toString(),
                lastCommitter = null
            )
        )

        every { cache.getFileTreeAtRoot(1L, "abc123", GitRefUtils.toFullRefOrNull("main")) } returns treeNodes

        // when
        val result = service.getFileTreeAtRoot(1L, "abc123", "main")

        // then
        result.size shouldBe 1
        result[0].name shouldBe "file.txt"
    }

    @Test
    fun `배치 커밋 해시 조회 성공`() {
        // given
        val commit1 = CommitResponse(
            hash = "abc123",
            message = "커밋1",
            committedAt = Instant.now().atOffset(ZoneOffset.UTC).toLocalDateTime(),
            author = mockk()
        )
        val commit2 = CommitResponse(
            hash = "def456",
            message = "커밋2",
            committedAt = Instant.now().atOffset(ZoneOffset.UTC).toLocalDateTime(),
            author = mockk()
        )

        every { commitQueryRepository.getLatestCommitBatch(1L, any()) } returns mapOf(
            "refs/heads/main" to commit1,
            "refs/heads/feature" to commit2
        )

        // when
        val result = service.getLatestCommitHashBatch(1L, listOf("main", "feature"))

        // then
        result.size shouldBe 2
        result["main"]?.hash shouldBe "abc123"
        result["feature"]?.hash shouldBe "def456"
    }

    @Test
    fun `배치 커밋 해시 조회 - 빈 리스트`() {
        // when
        val result = service.getLatestCommitHashBatch(1L, emptyList())

        // then
        result.size shouldBe 0
        verify(exactly = 0) { commitQueryRepository.getLatestCommitBatch(any(), any()) }
    }
}
