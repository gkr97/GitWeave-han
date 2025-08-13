package com.example.gitserver.module.repository.application.query

import com.example.gitserver.module.common.service.CommonCodeCacheService
import com.example.gitserver.module.gitindex.application.service.impl.GitArchiveService
import com.example.gitserver.module.repository.domain.vo.DownloadInfo
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.repository.exception.*
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RepositoryDownloadQueryService(
    private val repositoryRepository: RepositoryRepository,
    private val collaboratorRepository: CollaboratorRepository,
    private val gitArchiveService: GitArchiveService,
    private val commonCodeCacheService: CommonCodeCacheService,
) {
    private val log = KotlinLogging.logger {}

    /**
     * 저장소 다운로드 준비
     * @param repoId 저장소 ID
     * @param branch 브랜치 이름
     * @param userId 사용자 ID (null이면 익명)
     * @return DownloadInfo 객체
     */
    @Transactional(readOnly = true)
    fun prepareDownload(repoId: Long, branch: String, userId: Long?): DownloadInfo {
        log.info { "[Download] 요청: repoId=$repoId, branch=$branch, userId=$userId" }

        val repo = repositoryRepository.findByIdAndIsDeletedFalse(repoId)
            ?: run {
                log.warn { "[Download] 저장소 없음: repoId=$repoId, userId=$userId" }
                throw RepositoryNotFoundException(repoId)
            }

        val isOwner = (userId != null && repo.owner.id == userId)
        val isCollaborator = (userId != null &&
                collaboratorRepository.existsByRepositoryIdAndUserId(repoId, userId))

        val publicCodeId = commonCodeCacheService
            .getCodeDetailsOrLoad("VISIBILITY")
            .firstOrNull { it.code.equals("PUBLIC", true) }
            ?.id
            ?: run {
                log.error { "[Download] PUBLIC visibility code not found in cache (group=VISIBILITY)" }
                throw InvalidVisibilityCodeException("PUBLIC")
            }

        val isPublic = (repo.visibilityCodeId == publicCodeId)

        if (!isPublic && !isOwner && !isCollaborator) {
            log.warn {
                "[Download] 권한 없음: repoId=$repoId, userId=${userId ?: "anon"} " +
                        "public=$isPublic owner=$isOwner collaborator=$isCollaborator"
            }
            throw RepositoryAccessDeniedException(repoId, userId)
        }

        log.info {
            "[Download] 권한 확인 통과: repoId=$repoId, branch=$branch, userId=$userId, " +
                    "isPublic=$isPublic, isOwner=$isOwner, isCollaborator=$isCollaborator"
        }

        return DownloadInfo(
            filename = "${repo.name}-$branch.zip",
            streamSupplier = {
                try {
                    log.info { "[Download] archive stream 생성 시작: repoId=$repoId, branch=$branch, userId=$userId" }
                    gitArchiveService.createArchiveStream(repo.owner.id, repo.name, branch)
                } catch (e: Exception) {
                    log.error(e) { "[Download] archive stream 생성 실패: repoId=$repoId, branch=$branch" }
                    throw ArchiveCreationFailedException(repoId, branch)
                }
            }
        )
    }
}
