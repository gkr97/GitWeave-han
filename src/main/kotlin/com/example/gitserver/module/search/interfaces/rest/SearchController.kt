package com.example.gitserver.module.search.interfaces.rest

import com.example.gitserver.module.search.application.service.RepositorySearchQueryService
import com.example.gitserver.module.search.application.service.UserSearchQueryService
import com.example.gitserver.module.search.domain.RepositoryDoc
import com.example.gitserver.module.search.interfaces.dto.UserSearchDto
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/search")
class SearchController(
    private val repoSearchService: RepositorySearchQueryService,
    private val userSearchService: UserSearchQueryService
) {

    data class SearchSection<T>(
        val items: List<T>,
        val total: Long,
        val page: Int,
        val pageSize: Int,
        val hasNext: Boolean
    )

    data class SearchResponse(
        val repos: SearchSection<RepositoryDoc>?,
        val users: SearchSection<UserSearchDto>?
    )

    @GetMapping
    fun search(
        @RequestParam q: String,
        @RequestParam(defaultValue = "all") type: String,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int
    ): SearchResponse {

        if (q.isBlank()) {
            return SearchResponse(null, null)
        }

        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceIn(1, 100)
        val from = (safePage - 1) * safeSize

        val searchRepos = type == "repos" || type == "all"
        val searchUsers = type == "users" || type == "all"

        try {
            val repoSection =
                if (searchRepos) {
                    val (items, total) =
                        repoSearchService.searchRepositories(q, from, safeSize)
                    SearchSection(
                        items = items,
                        total = total,
                        page = safePage,
                        pageSize = safeSize,
                        hasNext = from + items.size < total
                    )
                } else null

            val userSection =
                if (searchUsers) {
                    val (items, total) =
                        userSearchService.searchUsers(q, from, safeSize)
                    SearchSection(
                        items = items,
                        total = total,
                        page = safePage,
                        pageSize = safeSize,
                        hasNext = from + items.size < total
                    )
                } else null

            return SearchResponse(
                repos = repoSection,
                users = userSection
            )

        } catch (e: Exception) {
            throw ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Search service unavailable"
            )
        }
    }
}
