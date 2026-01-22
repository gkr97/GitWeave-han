package com.example.gitserver.module.search.interfaces.rest

import com.example.gitserver.module.search.application.service.RepositorySearchQueryService
import com.example.gitserver.module.search.domain.RepositoryDoc
import org.springframework.web.bind.annotation.*

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
        @RequestParam(required = false, defaultValue = "1") page: Int,
        @RequestParam(required = false, defaultValue = "20") pageSize: Int
    ): SearchResponse {
        val safePage = if (page < 1) 1 else page
        val safeSize = when {
            pageSize <= 0 -> 20
            pageSize > 100 -> 100
            else -> pageSize
        }

        val from = (safePage - 1) * safeSize
        val (items, total) = searchService.searchRepositories(q, from = from, size = safeSize)

        val hasNext = (from + items.size) < total
        return SearchResponse(items = items, total = total, page = safePage, pageSize = safeSize, hasNext = hasNext)
    }
}