package com.example.gitserver.module.repository.interfaces.graphql

import com.example.gitserver.module.repository.application.query.RepositoryQueryService
import com.example.gitserver.module.repository.interfaces.dto.RepoDetailResponse
import com.example.gitserver.module.user.domain.CustomUserDetails
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller

@Controller
class RepositoryAccessResolver(
    private val repositoryQueryService: RepositoryQueryService,
) {
    @SchemaMapping(typeName = "RepositoryResponse", field = "isOwner")
    fun isOwner(repository: RepoDetailResponse, @AuthenticationPrincipal user: CustomUserDetails?): Boolean =
        repositoryQueryService.isOwner(repository.id, user?.getUserId())

    @SchemaMapping(typeName = "RepositoryResponse", field = "isStarred")
    fun isStarred(repository: RepoDetailResponse, @AuthenticationPrincipal user: CustomUserDetails?): Boolean =
        repositoryQueryService.isStarred(repository.id, user?.getUserId())

    @SchemaMapping(typeName = "RepositoryResponse", field = "isInvited")
    fun isInvited(repository: RepoDetailResponse, @AuthenticationPrincipal user: CustomUserDetails?): Boolean =
        repositoryQueryService.isInvited(repository.id, user?.getUserId())
}
