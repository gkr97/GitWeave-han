package com.example.gitserver.module.gitindex.domain.service.impl

import com.example.gitserver.module.gitindex.domain.service.GitIndexWriter
import com.example.gitserver.module.gitindex.exception.GitRepositoryOpenException
import com.example.gitserver.module.gitindex.infrastructure.s3.S3BlobUploader
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.any
import org.mockito.kotlin.mock

import org.mockito.kotlin.whenever
import java.nio.file.Files
import kotlin.test.Test

class BlobIndexerServiceImplTest {

    private lateinit var gitIndexWriter: GitIndexWriter
    private lateinit var blobUploader: S3BlobUploader
    private lateinit var userRepository: UserRepository
    private lateinit var blobIndexer: BlobIndexerServiceImpl

    @BeforeEach
    fun setUp() {
        gitIndexWriter = mock()
        blobUploader = mock()
        userRepository = mock()
        blobIndexer = BlobIndexerServiceImpl(gitIndexWriter, blobUploader, userRepository)
    }

    @Test
    fun `indexRepository에서 GitRepositoryOpenException 발생`() {
        // given
        val repoId = 1L
        val tempDir = Files.createTempDirectory("testrepo")

        whenever(userRepository.findByEmail(any())).thenReturn(null)

        // when & then
       assertThrows(GitRepositoryOpenException::class.java) {
            blobIndexer.indexRepository(repoId, tempDir)
        }
    }
}
