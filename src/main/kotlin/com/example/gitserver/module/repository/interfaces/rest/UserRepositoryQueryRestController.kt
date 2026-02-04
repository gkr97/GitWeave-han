package com.example.gitserver.module.repository.interfaces.rest

import com.example.gitserver.common.pagination.KeysetPaging
import com.example.gitserver.common.pagination.PagingValidator
import com.example.gitserver.common.response.ApiResponse
import com.example.gitserver.module.repository.application.query.RepositoryQueryService
import com.example.gitserver.module.repository.application.query.model.RepositoryListItemConnection
import com.example.gitserver.module.user.domain.CustomUserDetails
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserRepositoryQueryRestController(
    private val repositoryQueryService: RepositoryQueryService
) {
    @GetMapping("/{userId}/repositories")
    fun userRepositoriesConnection(
        @PathVariable userId: Long,
        @RequestParam(required = false) first: Int?,
        @RequestParam(required = false) after: String?,
        @RequestParam(required = false) last: Int?,
        @RequestParam(required = false) before: String?,
        @RequestParam(required = false) sortBy: String?,
        @RequestParam(required = false) sortDirection: String?,
        @RequestParam(required = false) keyword: String?,
        @AuthenticationPrincipal user: CustomUserDetails?
    ): ResponseEntity<ApiResponse<RepositoryListItemConnection>> {
        val paging = KeysetPaging(first = first, after = after, last = last, before = before)
        PagingValidator.validate(paging)

        val connection = repositoryQueryService.getUserRepositoriesConnection(
            targetUserId = userId,
            currentUserId = user?.getUserId(),
            paging = paging,
            sortBy = sortBy ?: "updatedAt",
            sortDirection = sortDirection ?: "DESC",
            keyword = keyword
        )
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), null, connection))
    }
}
