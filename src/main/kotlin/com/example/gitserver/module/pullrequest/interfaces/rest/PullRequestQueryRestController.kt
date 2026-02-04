package com.example.gitserver.module.pullrequest.interfaces.rest

import com.example.gitserver.common.response.ApiResponse
import com.example.gitserver.module.pullrequest.application.query.PullRequestQueryService
import com.example.gitserver.common.pagination.KeysetPaging
import com.example.gitserver.common.pagination.PageInfoDTO
import com.example.gitserver.common.pagination.PagingValidator
import com.example.gitserver.module.pullrequest.application.query.model.PullRequestListItemConnection
import com.example.gitserver.module.pullrequest.application.query.model.PullRequestListItemEdge
import com.example.gitserver.module.pullrequest.application.query.model.PullRequestDetail
import com.example.gitserver.module.pullrequest.application.query.model.PullRequestListItem
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/repositories/{repoId}/pull-requests")
class PullRequestQueryRestController(
    private val pullRequestQueryService: PullRequestQueryService
) {
    @GetMapping
    fun listPullRequests(
        @PathVariable repoId: Long,
        @RequestParam(required = false) first: Int?,
        @RequestParam(required = false) after: String?,
        @RequestParam(required = false) last: Int?,
        @RequestParam(required = false) before: String?,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) direction: String?
    ): ResponseEntity<ApiResponse<PullRequestListItemConnection>> {
        val offsetCursor = pullRequestQueryService.decodeOffsetCursor(after)
            ?: pullRequestQueryService.decodeOffsetCursor(before)

        val useOffset = page != null || size != null || offsetCursor != null
        if (useOffset) {
            val s = (size ?: offsetCursor?.second ?: 20).coerceIn(1, 100)
            val p = page ?: offsetCursor?.let { (it.first + 1) / s } ?: 0

            val (list, total) = pullRequestQueryService.getList(
                repositoryId = repoId,
                keyword = keyword,
                status = status,
                sort = sort,
                direction = direction,
                page = p,
                size = s
            )

            val baseOffset = p * s
            val edges = list.mapIndexed { idx, item ->
                PullRequestListItemEdge(
                    cursor = pullRequestQueryService.encodeOffsetCursor(baseOffset + idx, s),
                    node = item
                )
            }

            val pages = if (total <= 0) 0 else kotlin.math.ceil(total / s.toDouble()).toInt()
            val pageInfo = PageInfoDTO(
                hasNextPage = (p + 1) < pages,
                hasPreviousPage = p > 0,
                startCursor = edges.firstOrNull()?.cursor,
                endCursor = edges.lastOrNull()?.cursor
            )

            val connection = PullRequestListItemConnection(edges = edges, pageInfo = pageInfo, totalCount = total)
            return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), null, connection))
        }

        val paging = KeysetPaging(first = first, after = after, last = last, before = before)
        PagingValidator.validate(paging)

        val connection = pullRequestQueryService.getConnection(
            repositoryId = repoId,
            keyword = keyword,
            status = status,
            sort = sort,
            direction = direction,
            paging = paging
        )
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), null, connection))
    }

    @GetMapping("/{prId}")
    fun getPullRequest(
        @PathVariable repoId: Long,
        @PathVariable prId: Long
    ): ResponseEntity<ApiResponse<PullRequestDetail?>> {
        val detail = pullRequestQueryService.getDetail(repoId, prId)
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), null, detail))
    }

}
