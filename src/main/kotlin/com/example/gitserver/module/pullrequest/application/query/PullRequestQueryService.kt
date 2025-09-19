package com.example.gitserver.module.pullrequest.application.query

import com.example.gitserver.module.pullrequest.application.query.model.PullRequestDetail
import com.example.gitserver.module.pullrequest.application.query.model.PullRequestListItem
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestJdbcRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PullRequestQueryService(
    private val repoRepo: RepositoryRepository,
    private val prJdbc: PullRequestJdbcRepository,
) {
    private val log = KotlinLogging.logger {}

    private fun normalizeSort(sort: String?): String =
        when (sort?.lowercase()) {
            "title"     -> "title"
            "createdat" -> "createdAt"
            "updatedat" -> "updatedAt"
            null, ""    -> "updatedAt"
            else        -> "updatedAt"
        }

    private fun normalizeDir(direction: String?): String =
        if (direction.equals("ASC", ignoreCase = true)) "ASC" else "DESC"

    private fun normalizePage(page: Int?) = (page ?: 0).coerceAtLeast(0)
    private fun normalizeSize(size: Int?) = (size ?: 20).coerceIn(1, 100)

    /**
     * PR 목록 조회
     * - 존재하지 않는 저장소면 빈 목록 반환(혹은 예외)
     * - keyword/status/sort/dir/page/size 적용
     */
    @Transactional(readOnly = true)
    fun getList(
        repositoryId: Long,
        keyword: String?,
        status: String?,
        sort: String?,
        direction: String?,
        page: Int?,
        size: Int?
    ): Pair<List<PullRequestListItem>, Int> {
        val repoExists = repoRepo.existsById(repositoryId)
        if (!repoExists) {
            log.warn { "[PR][getList] repository not found: repoId=$repositoryId" }
            return emptyList<PullRequestListItem>() to 0
        }

        val s = normalizeSize(size)
        val p = normalizePage(page)
        val sortKey = normalizeSort(sort)
        val dir = normalizeDir(direction)

        val (content, total) = prJdbc.queryList(
            repositoryId = repositoryId,
            keyword = keyword?.takeIf { it.isNotBlank() },
            status = status?.takeIf { it.isNotBlank() },
            sort = sortKey,
            dir = dir,
            page = p,
            size = s
        )
        return content to total
    }

    /**
     * PR 상세 조회
     * - 저장소/PR 일치 검증
     */
    @Transactional(readOnly = true)
    fun getDetail(repositoryId: Long, prId: Long): PullRequestDetail? {
        val repoExists = repoRepo.existsById(repositoryId)
        if (!repoExists) {
            log.warn { "[PR][getDetail] repository not found: repoId=$repositoryId" }
            return null
        }
        return prJdbc.queryDetail(repositoryId, prId)
    }
}
