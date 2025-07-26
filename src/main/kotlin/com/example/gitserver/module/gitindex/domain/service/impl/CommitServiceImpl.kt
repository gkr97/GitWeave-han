package com.example.gitserver.module.gitindex.domain.service.impl

import com.example.gitserver.module.gitindex.domain.service.CommitService
import com.example.gitserver.module.gitindex.infrastructure.dynamodb.CommitQueryRepository
import com.example.gitserver.module.gitindex.infrastructure.dynamodb.TreeQueryRepository
import com.example.gitserver.module.repository.interfaces.dto.CommitResponse
import com.example.gitserver.module.repository.interfaces.dto.TreeNodeResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CommitServiceImpl(
    private val commitQueryRepository: CommitQueryRepository,
    private val treeQueryRepository: TreeQueryRepository
) : CommitService {

    @Transactional(readOnly = true)
    override fun getFileTreeAtRoot(repoId: Long, commitHash: String): List<TreeNodeResponse> {
        return treeQueryRepository.getFileTreeAtRoot(repoId, commitHash)
    }

    @Transactional(readOnly = true)
    override fun getLatestCommitHash(repositoryId: Long, branch: String): CommitResponse? {
        return commitQueryRepository.getLatestCommit(repositoryId, branch)
    }
}
