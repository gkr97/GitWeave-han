package com.example.gitserver.module.pullrequest.application.query

import com.example.gitserver.module.gitindex.domain.port.GitDiffPort
import com.example.gitserver.module.pullrequest.application.query.support.ParsedHunk
import com.example.gitserver.module.pullrequest.application.query.support.UnifiedDiffParser
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestRepository
import com.example.gitserver.module.repository.exception.RepositoryNotFoundException
import com.example.gitserver.module.gitindex.infrastructure.git.GitPathResolver
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PullRequestDiffQueryService(
    private val pullRequestRepository: PullRequestRepository,
    private val gitDiffPort: GitDiffPort,
    private val gitPathResolver: GitPathResolver
) {
    private val log = KotlinLogging.logger {}

    companion object {
        const val MAX_TOTAL_LINES = 5000   // 전체 라인 수 상한
        const val MAX_FILES = 200          // 파일 수 상한
    }

    /**
     * 특정 Pull Request의 특정 파일에 대한 파싱된 Hunk 목록을 반환합니다.
     *
     * @param repositoryId 저장소 ID
     * @param prId Pull Request ID
     * @param path 파일 경로
     * @param totalFiles PR의 전체 파일 수 (옵션, 파일 수 제한 검사용)
     * @param forceFull true면 제한 없이 전체 결과 반환, false면 제한 적용
     * @return 파싱된 Hunk 목록과 제한 초과 여부 (제한 초과 시 빈 목록과 true 반환)
     * @throws RepositoryNotFoundException 저장소가 존재하지 않거나 PR이 해당 저장소에 속하지 않는 경우
     */
    @Transactional(readOnly = true)
    fun getFileParsedHunks(
        repositoryId: Long,
        prId: Long,
        path: String,
        totalFiles: Int? = null,
        forceFull: Boolean = false
    ): Pair<List<ParsedHunk>, Boolean> {
        val pr = pullRequestRepository.findById(prId)
            .orElseThrow { RepositoryNotFoundException(prId) }

        if (pr.repository.id != repositoryId) {
            throw RepositoryNotFoundException(repositoryId)
        }

        if (!forceFull && totalFiles != null && totalFiles > MAX_FILES) {
            log.warn { "[PR getFileParsedHunks] prId=$prId 파일 개수 초과 ($totalFiles > $MAX_FILES)" }
            return emptyList<ParsedHunk>() to true
        }

        val base = pr.baseCommitHash ?: error("PR base 커밋이 없습니다. prId=$prId")
        val head = pr.headCommitHash ?: error("PR head 커밋이 없습니다. prId=$prId")

        val bareGitPath = gitPathResolver.bareDir(pr.repository.owner.id, pr.repository.name)
        val patchBytes = try {
            gitDiffPort.renderPatch(bareGitPath, base, head, path)
        } catch (e: org.eclipse.jgit.errors.RepositoryNotFoundException) {
            log.warn(e) { "[PR getFileParsedHunks] bare repo not found: $bareGitPath" }
            throw RepositoryNotFoundException(repositoryId)
        }

        if (patchBytes.isEmpty()) return emptyList<ParsedHunk>() to false

        val parsed = UnifiedDiffParser.parse(patchBytes.toString(Charsets.UTF_8))

        if (!forceFull) {
            val totalLines = parsed.sumOf { it.lines.size }
            if (totalLines > MAX_TOTAL_LINES) {
                log.warn { "[PR getFileParsedHunks] prId=$prId path=$path 라인 수 초과 ($totalLines > $MAX_TOTAL_LINES)" }
                return emptyList<ParsedHunk>() to true
            }
        }

        return parsed to false
    }

}
