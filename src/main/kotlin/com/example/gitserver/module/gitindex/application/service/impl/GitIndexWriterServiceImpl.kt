package com.example.gitserver.module.gitindex.application.service.impl

import com.example.gitserver.module.gitindex.domain.Blob
import com.example.gitserver.module.gitindex.domain.BlobTree
import com.example.gitserver.module.gitindex.domain.Commit
import com.example.gitserver.module.gitindex.application.service.GitIndexWriter
import com.example.gitserver.module.gitindex.infrastructure.dynamodb.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GitIndexWriterServiceImpl(
    private val commitRepo: CommitCommandRepository,
    private val blobRepo: BlobCommandRepository,
    private val treeRepo: TreeCommandRepository,
    private val txRepo: GitIndexTxRepository,
    private val commitQueryRepo: CommitQueryRepository,
) : GitIndexWriter {

    /**
     * 커밋 정보를 저장합니다.
     * @param commit 저장할 커밋 객체
     */
    override fun saveCommit(commit: Commit) {
        commitRepo.save(commit)
    }

    /**
     * 블롭 정보를 저장합니다.
     * @param blob 저장할 블롭 객체
     */
    override fun saveBlob(blob: Blob) {
        blobRepo.save(blob)
    }

    /**
     * 블롭 트리를 저장합니다.
     * @param tree 저장할 블롭 트리 객체
     */
    override fun saveTree(tree: BlobTree) {
        treeRepo.save(tree)
    }

    /**
     * 블롭과 블롭 트리를 동시에 저장합니다.
     * @param blob 저장할 블롭 객체
     * @param tree 저장할 블롭 트리 객체
     */
    override fun saveBlobAndTree(blob: Blob, tree: BlobTree) {
        txRepo.saveBlobAndTree(blob, tree)
    }

    /**
     * 특정 커밋 해시가 존재하는지 확인합니다.
     * @param repositoryId 저장소 ID
     * @param commitHash 확인할 커밋 해시
     * @return 존재 여부
     */
    override fun existsCommit(repositoryId: Long, commitHash: String): Boolean {
        return commitQueryRepo.existsCommit(repositoryId, commitHash)
    }

}
