package com.example.gitserver.module.repository.interfaces.graphql

import com.example.gitserver.common.pagination.KeysetPaging
import com.example.gitserver.common.pagination.PagingValidator
import com.example.gitserver.common.pagination.SortDirection
import com.example.gitserver.module.repository.application.query.BranchQueryService
import com.example.gitserver.module.repository.application.query.model.BranchResponseConnection
import com.example.gitserver.module.repository.application.query.model.BranchSortBy
import com.example.gitserver.module.repository.interfaces.dto.RepoDetailResponse
import com.example.gitserver.module.user.domain.CustomUserDetails
import org.springframework.graphql.data.method.annotation.ContextValue
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

@Controller
class RepositoryBranchQueryResolver(
    private val branchQueryService: BranchQueryService
) {
    @SchemaMapping(typeName = "RepositoryResponse", field = "branchesConnection")
    fun branchesConnection(
        repo: RepoDetailResponse,
        @Argument first: Int?,
        @Argument after: String?,
        @Argument last: Int?,
        @Argument before: String?,
        @Argument sortBy: BranchSortBy?,
        @Argument sortDirection: SortDirection?,
        @Argument keyword: String?,
        @Argument onlyMine: Boolean?,
        @ContextValue(name = "currentUser", required = false) user: CustomUserDetails?
    ): BranchResponseConnection {
        val paging = KeysetPaging(first = first, after = after, last = last, before = before)
        PagingValidator.validate(paging)

        return branchQueryService.getBranchConnection(
            repositoryId = repo.id,
            paging = paging,
            sortBy = (sortBy ?: BranchSortBy.LAST_COMMIT_AT).name,
            sortDirection = (sortDirection ?: SortDirection.DESC).name,
            keyword = keyword,
            onlyMine = onlyMine ?: false,
            currentUserId = user?.getUserId()
        )
    }
}
