package com.example.gitserver.module.repository.application.query

import com.example.gitserver.module.common.service.CommonCodeCacheService
import com.example.gitserver.module.gitindex.application.service.impl.GitArchiveService
import com.example.gitserver.module.repository.domain.vo.DownloadInfo
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import org.springframework.stereotype.Service
import java.nio.file.AccessDeniedException

import mu.KotlinLogging
import org.springframework.transaction.annotation.Transactional

@Service
class RepositoryDownloadQueryService(
    private val repositoryRepository: RepositoryRepository,
    private val collaboratorRepository: CollaboratorRepository,
    private val gitArchiveService: GitArchiveService,
    private val commonCodeCacheService: CommonCodeCacheService,
) {
    private val log = KotlinLogging.logger {}

    @Transactional(readOnly = true)
    fun prepareDownload(repoId: Long, branch: String, userId: Long?): DownloadInfo {
        log.info { "[Download] 요청: repoId=$repoId, branch=$branch, userId=$userId" }

        val repo = repositoryRepository.findByIdAndIsDeletedFalse(repoId)
            ?: run {
                log.warn { "[Download] 저장소 없음: repoId=$repoId, userId=$userId" }
                throw IllegalArgumentException("저장소 없음")
            }
        val isOwner = userId != null && repo.owner.id == userId
        val isCollaborator = userId != null && collaboratorRepository.existsByRepositoryIdAndUserId(repoId, userId)

        val isPublic = commonCodeCacheService.getCodeDetailsOrLoad("VISIBILITY")
            .firstOrNull { it.id == repo.visibilityCodeId }?.code == "public"
        if (!isPublic && !isOwner && !isCollaborator) {
            log.warn { "[Download] 권한 없음: repoId=$repoId, userId=$userId" }
            throw AccessDeniedException("권한 없음")
        }

        log.info { "[Download] 권한 확인 통과: repoId=$repoId, branch=$branch, userId=$userId, isOwner=$isOwner, isCollaborator=$isCollaborator" }

        return DownloadInfo(
            filename = "${repo.name}-$branch.zip",
            streamSupplier = {
                log.info { "[Download] archive stream 생성 시작: repoId=$repoId, branch=$branch, userId=$userId" }
                gitArchiveService.createArchiveStream(repo.owner.id, repo.name, branch)
            }
        )
    }
}
