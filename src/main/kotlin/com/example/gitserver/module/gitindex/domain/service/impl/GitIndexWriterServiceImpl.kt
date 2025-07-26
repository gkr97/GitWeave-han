package com.example.gitserver.module.gitindex.domain.service.impl

import com.example.gitserver.module.gitindex.domain.Blob
import com.example.gitserver.module.gitindex.domain.BlobTree
import com.example.gitserver.module.gitindex.domain.Commit
import com.example.gitserver.module.gitindex.domain.service.GitIndexWriter
import com.example.gitserver.module.gitindex.infrastructure.dynamodb.BlobDynamoRepository
import com.example.gitserver.module.gitindex.infrastructure.dynamodb.CommitDynamoRepository
import com.example.gitserver.module.gitindex.infrastructure.dynamodb.TreeDynamoRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GitIndexWriterServiceImpl(
    private val commitRepo: CommitDynamoRepository,
    private val blobRepo: BlobDynamoRepository,
    private val treeRepo: TreeDynamoRepository
) : GitIndexWriter {

    @Transactional
    override fun saveCommit(commit: Commit) {
        commitRepo.save(commit)
    }

    @Transactional
    override fun saveBlob(blob: Blob) {
        blobRepo.save(blob)
    }

    @Transactional
    override fun saveTree(tree: BlobTree) {
        treeRepo.save(tree)
    }
}
