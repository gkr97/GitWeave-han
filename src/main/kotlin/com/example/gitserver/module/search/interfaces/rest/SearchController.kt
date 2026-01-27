package com.example.gitserver.module.search.interfaces.rest

import com.example.gitserver.module.search.application.service.RepositorySearchQueryService
import com.example.gitserver.module.search.domain.RepositoryDoc
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/search")
class SearchController(
    private val searchService: RepositorySearchQueryService
) {

    data class SearchResponse(
        val items: List<RepositoryDoc>,
        val total: Long,
        val page: Int,
        val pageSize: Int,
        val hasNext: Boolean
    )

    @GetMapping
    fun search(
        @RequestParam q: String,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int
    ): SearchResponse {

        if (q.isBlank()) {
            return SearchResponse(
                items = emptyList(),
                total = 0,
                page = 1,
                pageSize = pageSize,
                hasNext = false
            )
        }

        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceIn(1, 100)

        val from = (safePage - 1) * safeSize

        try {
            val (items, total) =
                searchService.searchRepositories(q, from = from, size = safeSize)

            val hasNext = (from + items.size) < total

            return SearchResponse(
                items = items,
                total = total,
                page = safePage,
                pageSize = safeSize,
                hasNext = hasNext
            )

        } catch (e: Exception) {
            throw ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Search service unavailable"
            )
        }
    }
}
