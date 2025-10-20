package com.example.gitserver.module.gitindex.domain.port

import com.example.gitserver.module.gitindex.domain.Blob
import com.example.gitserver.module.gitindex.domain.BlobTree
import com.example.gitserver.module.gitindex.domain.Commit
import com.example.gitserver.module.gitindex.domain.vo.CommitHash

interface IndexTxRepository {
    fun saveBlobAndTree(blob: Blob, tree: BlobTree)

    fun prepareCommit(commit: Commit)

    fun sealCommitAndUpdateRef(
        repoId: Long,
        branch: String,
        commitHash: CommitHash,
        expectedOld: CommitHash? = null
    ): Boolean

    fun saveBlobOnly(blob: Blob)
}
