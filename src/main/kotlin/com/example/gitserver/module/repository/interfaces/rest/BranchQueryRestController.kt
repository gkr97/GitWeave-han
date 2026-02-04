package com.example.gitserver.module.repository.interfaces.rest

import com.example.gitserver.common.pagination.KeysetPaging
import com.example.gitserver.common.pagination.PagingValidator
import com.example.gitserver.common.pagination.SortDirection
import com.example.gitserver.common.response.ApiResponse
import com.example.gitserver.module.repository.application.query.BranchQueryService
import com.example.gitserver.module.repository.application.query.model.BranchResponseConnection
import com.example.gitserver.module.repository.application.query.model.BranchSortBy
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
@RequestMapping("/api/v1/repositories/{repoId}/branches")
class BranchQueryRestController(
    private val branchQueryService: BranchQueryService
) {
    @GetMapping
    fun listBranches(
        @PathVariable repoId: Long,
        @RequestParam(required = false) first: Int?,
        @RequestParam(required = false) after: String?,
        @RequestParam(required = false) last: Int?,
        @RequestParam(required = false) before: String?,
        @RequestParam(required = false) sortBy: BranchSortBy?,
        @RequestParam(required = false) sortDirection: SortDirection?,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) onlyMine: Boolean?,
        @AuthenticationPrincipal user: CustomUserDetails?
    ): ResponseEntity<ApiResponse<BranchResponseConnection>> {
        val paging = KeysetPaging(first = first, after = after, last = last, before = before)
        PagingValidator.validate(paging)

        val connection = branchQueryService.getBranchConnection(
            repositoryId = repoId,
            paging = paging,
            sortBy = (sortBy ?: BranchSortBy.LAST_COMMIT_AT).name,
            sortDirection = (sortDirection ?: SortDirection.DESC).name,
            keyword = keyword,
            onlyMine = onlyMine ?: false,
            currentUserId = user?.getUserId()
        )
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), null, connection))
    }
}
