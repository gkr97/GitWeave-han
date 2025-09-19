package com.example.gitserver.module.pullrequest.application.service

import com.example.gitserver.module.common.service.CommonCodeCacheService
import com.example.gitserver.module.gitindex.domain.port.GitDiffPort
import com.example.gitserver.module.pullrequest.application.query.model.PullRequestFileRow
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestFileJdbcIndexRepository
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestRepository
import com.example.gitserver.module.gitindex.infrastructure.git.GitPathResolver
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PullRequestIndexingService(
    private val gitDiffPort: GitDiffPort,
    private val fileIndexRepo: PullRequestFileJdbcIndexRepository,
    private val codeCache: CommonCodeCacheService,
    private val pullRequestRepository: PullRequestRepository,
    private val gitPathResolver: GitPathResolver
) {
    private val log = KotlinLogging.logger {}

    /**
     * PR 변경 파일 색인 갱신
     *
     * @param prId PR ID
     * @param repoId 저장소 ID
     * @param base 기준 커밋 해시
     * @param head 비교 커밋 해시
     */
    @Transactional
    fun reindex(prId: Long, repoId: Long, base: String, head: String) {
        val pr = pullRequestRepository.findById(prId)
            .orElseThrow { IllegalArgumentException("PR 없음: $prId") }

        val ownerId = pr.repository.owner.id
        val repoName = pr.repository.name
        val bareGitPath = gitPathResolver.bareDir(ownerId, repoName)

        log.debug { "[PR reindex] prId=$prId, repoId=$repoId, repo=$repoName, base=$base, head=$head, bare=$bareGitPath" }

        val diffs = try {
            gitDiffPort.listChangedFiles(bareGitPath, base, head)
        } catch (e: org.eclipse.jgit.errors.RepositoryNotFoundException) {
            log.warn(e) { "[PR reindex] bare repo not found: $bareGitPath" }
            throw IllegalArgumentException("Git 디렉터리가 존재하지 않습니다: $bareGitPath")
        }

        val codeMap = codeCache.getCodeDetailsOrLoad("PR_FILE_STATUS")
            .associateBy({ it.code.uppercase() }, { it.id })

        val rows = diffs.map { diff ->
            val statusKey = diff.status.name
            PullRequestFileRow(
                path = diff.path,
                oldPath = diff.oldPath,
                statusCodeId = codeMap[statusKey]
                    ?: error("알 수 없는 status: $statusKey"),
                isBinary = diff.isBinary,
                additions = diff.additions,
                deletions = diff.deletions,
                headBlobHash = diff.headBlobHash,
                baseBlobHash = diff.baseBlobHash
            )
        }

        fileIndexRepo.replaceAll(prId, rows)
    }

}
