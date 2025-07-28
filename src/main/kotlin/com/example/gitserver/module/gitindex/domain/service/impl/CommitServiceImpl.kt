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

    /**
     * 레포지토리의 루트 디렉토리에서 파일 트리를 가져옵니다.
     * @param repoId 레포지토리 ID
     * @param commitHash 커밋 해시
     * @return 파일 트리 노드 리스트
     */
    @Transactional(readOnly = true)
    override fun getFileTreeAtRoot(repoId: Long, commitHash: String): List<TreeNodeResponse> {
        return treeQueryRepository.getFileTreeAtRoot(repoId, commitHash)
    }

    /**
     * 마지막 커밋의 파일 트리를 가져옵니다.
     * @param repoId 레포지토리 ID
     * @param commitHash 커밋 해시
     * @return 파일 트리 노드 리스트
     */
    @Transactional(readOnly = true)
    override fun getLatestCommitHash(repositoryId: Long, branch: String): CommitResponse? {
        return commitQueryRepository.getLatestCommit(repositoryId, branch)
    }
}
