package com.example.gitserver.module.gitindex.domain.service.impl

import com.example.gitserver.module.gitindex.application.service.impl.GitIndexWriterServiceImpl
import com.example.gitserver.module.gitindex.domain.Blob
import com.example.gitserver.module.gitindex.domain.BlobTree
import com.example.gitserver.module.gitindex.domain.Commit
import com.example.gitserver.module.gitindex.infrastructure.dynamodb.BlobCommandRepository
import com.example.gitserver.module.gitindex.infrastructure.dynamodb.CommitCommandRepository
import com.example.gitserver.module.gitindex.infrastructure.dynamodb.TreeCommandRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class GitIndexWriterServiceImplTest {

    private lateinit var commitRepo: CommitCommandRepository
    private lateinit var blobRepo: BlobCommandRepository
    private lateinit var treeRepo: TreeCommandRepository
    private lateinit var service: GitIndexWriterServiceImpl

    @BeforeEach
    fun setUp() {
        commitRepo = mock()
        blobRepo = mock()
        treeRepo = mock()
        service = GitIndexWriterServiceImpl(commitRepo, blobRepo, treeRepo)
    }

    @Test
    fun `saveCommit은 commitRepo save를 호출`() {
        val commit = mock<Commit>()
        service.saveCommit(commit)
        verify(commitRepo).save(commit)
    }

    @Test
    fun `saveBlob은 blobRepo save를 호출`() {
        val blob = mock<Blob>()
        service.saveBlob(blob)
        verify(blobRepo).save(blob)
    }

    @Test
    fun `saveTree는 treeRepo save를 호출`() {
        val tree = mock<BlobTree>()
        service.saveTree(tree)
        verify(treeRepo).save(tree)
    }
}
