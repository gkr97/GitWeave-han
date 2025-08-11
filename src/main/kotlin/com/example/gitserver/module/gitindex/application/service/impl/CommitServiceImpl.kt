package com.example.gitserver.module.gitindex.application.service.impl

import com.example.gitserver.common.util.GitRefUtils.toFullRef
import com.example.gitserver.common.util.GitRefUtils.toFullRefOrNull
import com.example.gitserver.module.gitindex.application.service.CommitService
import com.example.gitserver.module.gitindex.infrastructure.dynamodb.CommitQueryRepository
import com.example.gitserver.module.gitindex.infrastructure.dynamodb.TreeQueryRepository
import com.example.gitserver.module.gitindex.infrastructure.redis.GitIndexCache
import com.example.gitserver.module.repository.interfaces.dto.CommitResponse
import com.example.gitserver.module.repository.interfaces.dto.TreeNodeResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CommitServiceImpl(
    private val commitQueryRepository: CommitQueryRepository,
    private val cache: GitIndexCache,
) : CommitService {


    /**
     * 레포지토리의 루트 디렉토리에서 파일 트리를 가져옵니다.
     */
    @Transactional(readOnly = true)
    override fun getFileTreeAtRoot(
        repoId: Long,
        commitHash: String,
        branch: String?
    ): List<TreeNodeResponse> {
        return cache.getFileTreeAtRoot(repoId, commitHash, toFullRefOrNull(branch))
    }

    /**
     * 마지막 커밋 정보를 가져옵니다.
     */
    @Transactional(readOnly = true)
    override fun getLatestCommitHash(repositoryId: Long, branch: String): CommitResponse? {
        return commitQueryRepository.getLatestCommit(repositoryId, toFullRef(branch))
    }

    @Transactional(readOnly = true)
    override fun getCommitInfo(repositoryId: Long, commitHash: String): CommitResponse? {
        return cache.getCommitByHash(repositoryId, commitHash)
    }
}
