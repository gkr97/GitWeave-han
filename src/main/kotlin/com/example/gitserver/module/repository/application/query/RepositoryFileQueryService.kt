package com.example.gitserver.module.repository.application.query

import com.example.gitserver.common.util.GitRefUtils
import com.example.gitserver.module.common.service.CommonCodeCacheService
import com.example.gitserver.module.gitindex.application.service.CommitService
import com.example.gitserver.module.gitindex.application.service.impl.FileContentService
import com.example.gitserver.module.gitindex.application.service.impl.FileTreeService
import com.example.gitserver.module.repository.exception.InvalidVisibilityCodeException
import com.example.gitserver.module.repository.exception.RepositoryAccessDeniedException
import com.example.gitserver.module.repository.exception.RepositoryNotFoundException
import com.example.gitserver.module.repository.infrastructure.persistence.BranchRepository
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.repository.interfaces.dto.FileContentResponse
import com.example.gitserver.module.repository.interfaces.dto.TreeNodeResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Locale

@Service
class RepositoryFileQueryService(
    private val fileTreeService: FileTreeService,
    private val fileContentService: FileContentService,
    private val repositoryRepository: RepositoryRepository,
    private val collaboratorRepository: CollaboratorRepository,
    private val commonCodeCacheService: CommonCodeCacheService,
    private val commitService: CommitService,
    private val branchRepository: BranchRepository,
) {

    /**
     * 파일/폴더 트리 조회
     * commitHash가 없으면 branch(없으면  기본 브랜치)로 최신 커밋 해시를 가져옴
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

        val effectiveHash = commitHash ?: resolveLatestCommitHash(repositoryId, branch)
        return fileTreeService.getFileTree(repositoryId, effectiveHash, path, branch)
    }

    /**
     * 파일 콘텐츠 조회
     * commitHash가 없으면 branch(없으면 기본 브랜치)로 최신 커밋 해시를 가져옴
     */
    @Transactional(readOnly = true)
    fun getFileContent(
        repositoryId: Long,
        commitHash: String?,
        path: String,
        branch: String?,
        userId: Long?
    ): FileContentResponse {
        checkFileTreePermission(repositoryId, userId)

        val effectiveHash = commitHash ?: resolveLatestCommitHash(repositoryId, branch)
        return fileContentService.getFileContent(repositoryId, effectiveHash, path, branch)
    }

    /**
     * branch → 최신 커밋 해시 해소
     * - branch가 null/blank면 저장소의 기본 브랜치 사용
     * - 짧은 이름/풀 레프 모두 허용
     * - 브랜치 존재 검증 포함
     */
    @Transactional(readOnly = true)
    fun resolveLatestCommitHash(repositoryId: Long, branch: String?): String {
        val repo = repositoryRepository.findByIdWithOwner(repositoryId)
            ?: throw RepositoryNotFoundException(repositoryId)

        val short = (branch?.let { GitRefUtils.toShortName(GitRefUtils.toFullRef(it)) } ?: repo.defaultBranch)

        val fullRef = GitRefUtils.toFullRef(short)

        if (branchRepository.findByRepositoryIdAndName(repositoryId, fullRef) == null) {
            throw RepositoryNotFoundException(repositoryId)
        }

        val latest = commitService.getLatestCommitHash(repositoryId, fullRef)
            ?: throw IllegalStateException("해당 브랜치에 커밋이 없습니다: $short")

        return latest.hash
    }

    /**
     * 권한 체크: PRIVATE이면 소유자 또는 수락한 협업자만 접근 가능
     */
    private fun checkFileTreePermission(repositoryId: Long, userId: Long?) {
        val repo = repositoryRepository.findByIdWithOwner(repositoryId)
            ?: throw RepositoryNotFoundException(repositoryId)

        val visibilityCode = commonCodeCacheService.getCodeDetailsOrLoad("VISIBILITY")
            .firstOrNull { it.id == repo.visibilityCodeId }
            ?.code
            ?: throw InvalidVisibilityCodeException(repo.visibilityCodeId?.toString())

        val visibility = visibilityCode.uppercase(Locale.ROOT)
        if (visibility == "PRIVATE") {
            val isOwner = repo.owner.id == userId
            val isCollaborator = userId?.let { collaboratorRepository.existsByRepositoryIdAndUserId(repositoryId, it) } ?: false
            if (!isOwner && !isCollaborator) {
                throw RepositoryAccessDeniedException(repositoryId, userId)
            }
        }
    }

}
