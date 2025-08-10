package com.example.gitserver.module.gitindex.application.service.impl

import com.example.gitserver.module.gitindex.infrastructure.dynamodb.TreeQueryRepository
import com.example.gitserver.module.repository.interfaces.dto.TreeNodeResponse
import org.springframework.stereotype.Service
import java.util.*

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
        val hash = requireNotNull(commitHash) { "commitHash는 필수입니다." }

        // 경로 정규화
        val normPath = path?.trim()?.trim('/')?.ifEmpty { null }

        val nodes =
            if (normPath == null) {
                // 루트는 depth=0 기준 인덱스 전용 쿼리 사용
                treeQueryRepository.getFileTreeAtRoot(repositoryId, hash, branch)
            } else {
                treeQueryRepository.getFileTree(repositoryId, hash, normPath, branch)
            }

        return nodes.sortedWith(
            compareBy<TreeNodeResponse>({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) })
        )
    }
}
