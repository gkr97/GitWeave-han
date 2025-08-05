package com.example.gitserver.module.gitindex.application.service.impl

import com.example.gitserver.module.gitindex.infrastructure.dynamodb.TreeQueryRepository
import com.example.gitserver.module.repository.interfaces.dto.TreeNodeResponse
import org.springframework.stereotype.Service

@Service
class FileTreeService(
    private val treeQueryRepository: TreeQueryRepository
) {
    /**
     * 저장소 파일/폴더 트리 조회
     */
    fun getFileTree(
        repositoryId: Long,
        commitHash: String?,
        path: String?,
        branch: String?
    ): List<TreeNodeResponse> {
        val hash = commitHash ?: throw IllegalArgumentException("commitHash는 필수입니다.")
        val treeNodes = treeQueryRepository.getFileTree(repositoryId, hash, path, branch)
        return treeNodes.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }
}
