package com.example.gitserver.module.gitindex.domain.service.impl

import com.example.gitserver.module.gitindex.application.service.impl.CommitServiceImpl
import com.example.gitserver.module.gitindex.application.service.CommitService
import com.example.gitserver.module.gitindex.infrastructure.dynamodb.CommitQueryRepository
import com.example.gitserver.module.gitindex.infrastructure.dynamodb.TreeQueryRepository
import com.example.gitserver.module.repository.interfaces.dto.CommitResponse
import com.example.gitserver.module.repository.interfaces.dto.RepositoryUserResponse
import com.example.gitserver.module.repository.interfaces.dto.TreeNodeResponse
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.Test

class CommitServiceImplTest {
     private lateinit var commitQueryRepository: CommitQueryRepository
     private lateinit var treeQueryRepository: TreeQueryRepository
     private lateinit var commitService: CommitService

     @BeforeEach
        fun setUp() {
            commitQueryRepository = mock()
            treeQueryRepository = mock()
            commitService = CommitServiceImpl(commitQueryRepository, treeQueryRepository)
        }


     @Test
     fun `getFileTreeAtRoot 정상 동작`() {
         // given
         val repoId = 1L
         val commitHash = "abc123"
         val branch = "main"
         val expectedTree = listOf(
             TreeNodeResponse("file1.txt", "blob", true, 100, "abc123"),
             TreeNodeResponse("dir1", "tree", false, 90, "def456"),
         )
         whenever(treeQueryRepository.getFileTreeAtRoot(repoId, commitHash, branch)).thenReturn(expectedTree)

         // when
         val result = commitService.getFileTreeAtRoot(repoId, commitHash, branch)

         // then
         assertNotNull(result)
         assertEquals(expectedTree, result)

     }

    @Test
    fun `getLatestCommitHash 정상 동작`() {
        // given
        val repositoryId = 1L
        val branch = "refs/heads/main"
        val expectedCommit = CommitResponse(
            hash = "abc123",
            message = "testuser",
            committedAt = LocalDateTime.now(),
            author = RepositoryUserResponse(
                userId = 1L,
                nickname = "testuser",
                profileImageUrl = "tests.com/profile.jpg"
            )
        )

        whenever(commitQueryRepository.getLatestCommit(repositoryId, branch)).thenReturn(expectedCommit)

        // when
        val result = commitService.getLatestCommitHash(repositoryId, branch)

        // then
        assertNotNull(result)
        assertEquals(expectedCommit, result)
    }

 }