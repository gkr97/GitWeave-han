package com.example.gitserver.module.pullrequest.application.query

import com.example.gitserver.module.common.application.service.CommonCodeCacheService
import com.example.gitserver.module.pullrequest.application.query.model.PullRequestFileDiffItem
import com.example.gitserver.module.pullrequest.application.query.model.PullRequestFileItem
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestFileJdbcRepository
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestRepository
import com.example.gitserver.module.repository.exception.RepositoryNotFoundException
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PullRequestFileQueryService(
    private val pullRequestRepository: PullRequestRepository,
    private val collaboratorRepository: CollaboratorRepository,
    private val commonCodeCacheService: CommonCodeCacheService,
    private val fileJdbcRepo: PullRequestFileJdbcRepository,
) {
    private val log = KotlinLogging.logger {}

    /**
     * 특정 Pull Request에 대한 변경 파일 목록을 조회합니다.
     *
     * @param prId Pull Request ID
     * @param currentUserId 현재 사용자 ID (인증된 경우)
     * @return 변경 파일 목록
     */
    @Transactional(readOnly = true)
    fun listFiles(prId: Long, currentUserId: Long?): List<PullRequestFileItem> {
        val pr = authorizeAndLoad(prId, currentUserId)
        val rows = fileJdbcRepo.findFiles(pr.id)
        return rows.map { r ->
            val safePath = when {
                hasPathField(r) -> r.path ?: r.oldPath
                else -> r.path ?: r.oldPath
            } ?: throw IllegalStateException("PR($prId) file row has neither path nor oldPath (id=${r.id})")
            if (hasPathField(r)) {
                r.copy(path = safePath)
            } else {
                r.copy(path = safePath)
            }
        }
    }

    /**
     * 특정 Pull Request에 대한 파일별 상세 변경 내역을 조회합니다.
     *
     * @param prId Pull Request ID
     * @param currentUserId 현재 사용자 ID (인증된 경우)
     * @return 파일별 상세 변경 내역 목록
     */
    @Transactional(readOnly = true)
    fun listDiffs(prId: Long, currentUserId: Long?): List<PullRequestFileDiffItem> {
        val pr = authorizeAndLoad(prId, currentUserId)
        val rows = fileJdbcRepo.findDiffs(pr.id)
        return rows.map { r ->
            val safePath = r.filePath ?: r.oldPath
            ?: throw IllegalStateException("PR($prId) diff row has neither filePath nor oldPath (id=${r.id})")
            r.copy(filePath = safePath)
        }
    }

    /**
     * 특정 Pull Request에 대한 요약 정보를 조회합니다.
     *
     * @param prId Pull Request ID
     * @param currentUserId 현재 사용자 ID (인증된 경우)
     * @return 요약 정보 (추가된 라인 수, 삭제된 라인 수, 파일 변경 수)
     */
    private fun authorizeAndLoad(prId: Long, currentUserId: Long?) =
        pullRequestRepository.findById(prId).orElseThrow {
            RepositoryNotFoundException(prId)
        }.also { pr ->
            val repo = pr.repository
            val publicId = commonCodeCacheService.getVisibilityCodeId("public")
            if (repo.visibilityCodeId == publicId) return@also

            if (currentUserId == null) throw SecurityException("인증 필요")

            val isOwner = repo.owner.id == currentUserId
            val isCollaborator = collaboratorRepository.existsByRepositoryIdAndUserId(repo.id, currentUserId)
            if (!isOwner && !isCollaborator) {
                throw SecurityException("접근 권한 없음: repoId=${repo.id}, userId=$currentUserId")
            }
        }

    private fun hasPathField(item: PullRequestFileItem): Boolean =
        try {
            item::class.members.any { it.name == "path" }
        } catch (_: Throwable) {
            false
        }
}
