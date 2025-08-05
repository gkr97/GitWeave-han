package com.example.gitserver.module.repository.application.query

import com.example.gitserver.module.common.service.CommonCodeCacheService
import com.example.gitserver.module.gitindex.application.service.impl.FileContentService
import com.example.gitserver.module.gitindex.application.service.impl.FileTreeService
import com.example.gitserver.module.repository.exception.InvalidVisibilityCodeException
import com.example.gitserver.module.repository.exception.RepositoryAccessDeniedException
import com.example.gitserver.module.repository.exception.RepositoryNotFoundException
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.repository.interfaces.dto.FileContentResponse
import com.example.gitserver.module.repository.interfaces.dto.TreeNodeResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RepositoryFileQueryService(
    private val fileTreeService: FileTreeService,
    private val fileContentService: FileContentService,
    private val repositoryRepository: RepositoryRepository,
    private val collaboratorRepository: CollaboratorRepository,
    private val commonCodeCacheService: CommonCodeCacheService,
) {

    /**
     * 파일/폴더 트리 조회
     */
    @Transactional(readOnly = true)
    fun getFileTree(
        repositoryId: Long,
        commitHash: String?,
        path: String?,
        branch: String?,
        userId: Long?
    ): List<TreeNodeResponse> {
        checkFileTreePermission(repositoryId, userId)
        return fileTreeService.getFileTree(repositoryId, commitHash, path, branch)
    }

    @Transactional(readOnly = true)
    fun getFileContent(
        repositoryId: Long,
        commitHash: String?,
        path: String,
        branch: String?,
        userId: Long?
    ): FileContentResponse {
        checkFileTreePermission(repositoryId, userId)
        return fileContentService.getFileContent(repositoryId, commitHash, path, branch)
    }

    private fun checkFileTreePermission(repositoryId: Long, userId: Long?) {
        val repo = repositoryRepository.findByIdWithOwner(repositoryId)
            ?: throw RepositoryNotFoundException(repositoryId)

        val visibility = commonCodeCacheService.getCodeDetailsOrLoad("VISIBILITY")
            .firstOrNull { it.id == repo.visibilityCodeId }
            ?.code
            ?.lowercase()
            ?: throw InvalidVisibilityCodeException(repo.visibilityCodeId?.toString())

        if (visibility == "private") {
            val isOwner = repo.owner.id == userId
            val isCollaborator = userId?.let { collaboratorRepository.existsByRepositoryIdAndUserId(repositoryId, it) } ?: false
            if (!isOwner && !isCollaborator) {
                throw RepositoryAccessDeniedException(repositoryId, userId)
            }
        }
    }

}
