package com.example.gitserver.module.repository.interfaces.graphql

import com.example.gitserver.module.repository.application.query.BranchQueryService
import com.example.gitserver.module.repository.interfaces.dto.BranchListPageResponse
import com.example.gitserver.module.user.domain.CustomUserDetails
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller

@Controller
class RepositoryBranchQueryResolver(
    private val branchQueryService: BranchQueryService
) {

    @QueryMapping
    fun repositoryBranches(
        @Argument repositoryId: Long,
        @Argument page: Int?,
        @Argument size: Int?,
        @Argument sortBy: String?,
        @Argument sortDirection: String?,
        @Argument keyword: String?,
        @Argument onlyMine: Boolean?,
        @AuthenticationPrincipal user: CustomUserDetails?
    ): BranchListPageResponse {
        return branchQueryService.getBranchList(
            repositoryId = repositoryId,
            page = page ?: 1,
            size = size ?: 20,
            sortBy = sortBy ?: "NAME",
            sortDirection = sortDirection ?: "DESC",
            keyword = keyword,
            onlyMine = onlyMine ?: false,
            currentUserId = user?.getUserId()
        )
    }
}

