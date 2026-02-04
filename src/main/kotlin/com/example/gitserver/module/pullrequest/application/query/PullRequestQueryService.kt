package com.example.gitserver.module.pullrequest.application.query

import com.example.gitserver.common.pagination.CursorCodec
import com.example.gitserver.common.pagination.CursorPayload
import com.example.gitserver.common.pagination.KeysetPaging
import com.example.gitserver.common.pagination.PageInfoDTO
import com.example.gitserver.common.pagination.PagingValidator
import com.example.gitserver.common.pagination.SortDirection
import com.example.gitserver.module.pullrequest.application.query.model.PullRequestDetail
import com.example.gitserver.module.pullrequest.application.query.model.PullRequestListItemEdge
import com.example.gitserver.module.pullrequest.application.query.model.PullRequestKeysetReq
import com.example.gitserver.module.pullrequest.application.query.model.PullRequestListItemConnection
import com.example.gitserver.module.pullrequest.application.query.model.PullRequestListItem
import com.example.gitserver.module.pullrequest.application.query.model.PullRequestSortBy
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestJdbcRepository
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestKeysetJdbcRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZoneOffset

@Service
class PullRequestQueryService(
    private val repoRepo: RepositoryRepository,
    private val prJdbc: PullRequestJdbcRepository,
    private val prKeysetJdbc: PullRequestKeysetJdbcRepository
) {
    private val log = KotlinLogging.logger {}

    private fun normalizeSort(sort: String?): PullRequestSortBy =
        when (sort?.lowercase()) {
            "title" -> PullRequestSortBy.TITLE
            "createdat" -> PullRequestSortBy.CREATED_AT
            "updatedat", null, "" -> PullRequestSortBy.UPDATED_AT
            else -> PullRequestSortBy.UPDATED_AT
        }

    private fun normalizeDir(direction: String?): SortDirection =
        if (direction.equals("ASC", ignoreCase = true)) SortDirection.ASC else SortDirection.DESC

    private fun normalizePage(page: Int?) = (page ?: 0).coerceAtLeast(0)
    private fun normalizeSize(size: Int?) = (size ?: 20).coerceIn(1, 100)

    /**
     * PR 목록 조회 (offset)
     * - 기존 REST/레거시 유지용
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
            sort = toSortKey(sortKey),
            dir = dir.name,
            page = p,
            size = s
        )
        return content to total
    }

    /**
     * PR 목록 조회 (keyset connection)
     */
    @Transactional(readOnly = true)
    fun getConnection(
        repositoryId: Long,
        keyword: String?,
        status: String?,
        sort: String?,
        direction: String?,
        paging: KeysetPaging
    ): PullRequestListItemConnection {
        val repoExists = repoRepo.existsById(repositoryId)
        if (!repoExists) {
            log.warn { "[PR][getConnection] repository not found: repoId=$repositoryId" }
            return PullRequestListItemConnection(emptyList(), PageInfoDTO(false, false, null, null), totalCount = 0)
        }

        try {
            PagingValidator.validate(paging)
        } catch (_: Exception) {
            return PullRequestListItemConnection(emptyList(), PageInfoDTO(false, false, null, null), totalCount = 0)
        }

        val sortKey = normalizeSort(sort)
        val dir = normalizeDir(direction)

        val rows = prKeysetJdbc.query(
            PullRequestKeysetReq(
                repoId = repositoryId,
                paging = paging,
                sort = sortKey,
                dir = dir,
                keyword = keyword?.takeIf { it.isNotBlank() },
                status = status?.takeIf { it.isNotBlank() }
            )
        )

        val pageSize = paging.pageSize
        val (slice, hasNextPage, hasPreviousPage) =
            if (paging.isForward) {
                val hasNext = rows.size > pageSize
                Triple(rows.take(pageSize), hasNext, paging.after != null)
            } else {
                val hasPrev = rows.size > pageSize
                Triple(rows.take(pageSize).asReversed(), paging.before != null, hasPrev)
            }

        val edges = slice.map { row ->
            PullRequestListItemEdge(
                cursor = cursorFromItem(row, sortKey, dir),
                node = row
            )
        }

        val pageInfo = PageInfoDTO(
            hasNextPage = hasNextPage,
            hasPreviousPage = hasPreviousPage,
            startCursor = edges.firstOrNull()?.cursor,
            endCursor = edges.lastOrNull()?.cursor
        )

        val total = prJdbc.count(
            repositoryId = repositoryId,
            keyword = keyword?.takeIf { it.isNotBlank() },
            status = status?.takeIf { it.isNotBlank() }
        )

        return PullRequestListItemConnection(edges = edges, pageInfo = pageInfo, totalCount = total)
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

    private fun cursorFromItem(item: PullRequestListItem, sort: PullRequestSortBy, dir: SortDirection): String {
        val payload = when (sort) {
            PullRequestSortBy.UPDATED_AT -> {
                val updated = (item.updatedAt ?: item.createdAt)
                    .atOffset(ZoneOffset.UTC)
                    .toInstant()
                    .toString()
                CursorPayload(
                    sort = sort.name,
                    dir = dir.name,
                    k = mapOf(
                        "updatedAt" to updated,
                        "id" to item.id.toString()
                    )
                )
            }

            PullRequestSortBy.CREATED_AT -> {
                val created = item.createdAt
                    .atOffset(ZoneOffset.UTC)
                    .toInstant()
                    .toString()
                CursorPayload(
                    sort = sort.name,
                    dir = dir.name,
                    k = mapOf(
                        "createdAt" to created,
                        "id" to item.id.toString()
                    )
                )
            }

            PullRequestSortBy.TITLE -> CursorPayload(
                sort = sort.name,
                dir = dir.name,
                k = mapOf(
                    "title" to item.title.lowercase(),
                    "id" to item.id.toString()
                )
            )
        }
        return CursorCodec.encode(payload)
    }

    fun encodeOffsetCursor(offset: Int, size: Int): String =
        CursorCodec.encode(
            CursorPayload(
                sort = "OFFSET",
                dir = "ASC",
                k = mapOf(
                    "offset" to offset.toString(),
                    "size" to size.toString()
                )
            )
        )

    fun decodeOffsetCursor(cursor: String?): Pair<Int, Int>? {
        if (cursor.isNullOrBlank()) return null
        return try {
            val payload = CursorCodec.decode(cursor)
            if (payload.sort != "OFFSET") return null
            val offset = payload.k["offset"]?.toIntOrNull() ?: return null
            val size = payload.k["size"]?.toIntOrNull() ?: return null
            offset to size
        } catch (_: Exception) {
            null
        }
    }

    private fun toSortKey(sort: PullRequestSortBy): String =
        when (sort) {
            PullRequestSortBy.TITLE -> "title"
            PullRequestSortBy.CREATED_AT -> "createdAt"
            PullRequestSortBy.UPDATED_AT -> "updatedAt"
        }
}
