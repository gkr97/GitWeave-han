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

    /**
     * 커밋 정보를 저장합니다.
     * @param commit 저장할 커밋 객체
     */
    @Transactional
    override fun saveCommit(commit: Commit) {
        commitRepo.save(commit)
    }

    /**
     * 블롭 정보를 저장합니다.
     * @param blob 저장할 블롭 객체
     */
    @Transactional
    override fun saveBlob(blob: Blob) {
        blobRepo.save(blob)
    }

    /**
     * 블롭 트리를 저장합니다.
     * @param tree 저장할 블롭 트리 객체
     */
    @Transactional
    override fun saveTree(tree: BlobTree) {
        treeRepo.save(tree)
    }
}
