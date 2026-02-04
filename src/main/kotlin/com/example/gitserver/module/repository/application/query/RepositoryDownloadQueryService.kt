package com.example.gitserver.module.repository.application.query

import com.example.gitserver.module.gitindex.storage.infrastructure.git.GitArchiveAdapter
import com.example.gitserver.module.repository.domain.vo.DownloadInfo
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.repository.exception.*
import com.example.gitserver.common.cache.RequestCache
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RepositoryDownloadQueryService(
    private val repositoryRepository: RepositoryRepository,
    private val gitArchiveAdapter: GitArchiveAdapter,
    private val repositoryAccessService: RepositoryAccessService,
    private val requestCache: RequestCache
) {
    private val log = KotlinLogging.logger {}

    @Transactional(readOnly = true)
    fun prepareDownload(repoId: Long, branch: String, userId: Long?): DownloadInfo {
        log.info { "[Download] 요청: repoId=$repoId, branch=$branch, userId=$userId" }

        val repo = requestCache.getRepo(repoId) ?: run {
            val loaded = repositoryRepository.findByIdAndIsDeletedFalse(repoId)
                ?: run {
                    log.warn { "[Download] 저장소 없음: repoId=$repoId, userId=$userId" }
                    throw RepositoryNotFoundException(repoId)
                }
            requestCache.putRepo(loaded)
            loaded
        }

        val hasAccess = repositoryAccessService.hasReadAccess(repo, userId, requireAccepted = true)
        if (!hasAccess) {
            log.warn { "[Download] 권한 없음: repoId=$repoId, userId=${userId ?: "anon"}" }
            throw RepositoryAccessDeniedException(repoId, userId)
        }

        return DownloadInfo(
            filename = "${repo.name}-$branch.zip",
            streamSupplier = {
                try {
                    log.info { "[Download] archive stream 생성 시작: repoId=$repoId, branch=$branch, userId=$userId" }
                    gitArchiveAdapter.createArchiveStream(repo.owner.id, repo.name, branch)
                } catch (e: Exception) {
                    log.error(e) { "[Download] archive stream 생성 실패: repoId=$repoId, branch=$branch" }
                    throw ArchiveCreationFailedException(repoId, branch)
                }
            }
        )
    }
}
