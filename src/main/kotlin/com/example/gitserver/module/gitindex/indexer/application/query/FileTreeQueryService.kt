package com.example.gitserver.module.gitindex.indexer.application.query

import com.example.gitserver.module.gitindex.shared.domain.port.TreeQueryRepository
import com.example.gitserver.module.repository.interfaces.dto.TreeNodeResponse
import org.springframework.stereotype.Service
import java.util.Locale

@Service
class FileTreeQueryService(
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
        val normPath = path?.trim()?.trim('/')?.ifEmpty { null }

        val nodes =
            if (normPath == null) {
                treeQueryRepository.getFileTreeAtRoot(repositoryId, hash, branch)
            } else {
                treeQueryRepository.getFileTree(repositoryId, hash, normPath, branch)
            }

        return nodes.sortedWith(
            compareBy<TreeNodeResponse>({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) })
        )
    }
}
