package com.example.gitserver.module.repository.interfaces.graphql

import com.example.gitserver.module.repository.application.query.RepositoryQueryService
import com.example.gitserver.module.repository.interfaces.dto.RepoDetailResponse
import com.example.gitserver.module.user.domain.CustomUserDetails
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller

@Controller
class RepositoryQueryResolver(
    private val repositoryQueryService: RepositoryQueryService
) {
    @QueryMapping
    fun repository(
        @Argument id: Long,
        @Argument branch: String?,
        @AuthenticationPrincipal user: CustomUserDetails?
    ): RepoDetailResponse {
        return repositoryQueryService.getRepository(id, branch, user?.getUserId())
    }
}