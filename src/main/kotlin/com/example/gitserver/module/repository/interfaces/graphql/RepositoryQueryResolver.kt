package com.example.gitserver.module.repository.interfaces.graphql

import com.example.gitserver.common.pagination.KeysetPaging
import com.example.gitserver.common.pagination.PagingValidator
import com.example.gitserver.module.repository.application.query.RepositoryQueryService
import com.example.gitserver.module.repository.application.query.model.RepositoryListItemConnection
import com.example.gitserver.module.repository.interfaces.dto.*
import com.example.gitserver.module.user.domain.CustomUserDetails
import com.example.gitserver.module.user.exception.UserLoginException
import graphql.schema.DataFetchingEnvironment
import org.springframework.graphql.data.method.annotation.ContextValue
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller
import org.springframework.validation.Validator

@Controller
class RepositoryQueryResolver(
    private val repositoryQueryService: RepositoryQueryService,
    private val validator: Validator
) {
    @QueryMapping
    fun repository(
        @Argument id: Long,
        @Argument branch: String?,
        @ContextValue(name = "currentUser", required = false) user: CustomUserDetails?,
        env: DataFetchingEnvironment
    ): RepoDetailResponse {
        val repo = repositoryQueryService.getRepository(id, branch, user?.getUserId())
        env.graphQlContext.put("repoDetail", repo)
        return repo
    }

    @QueryMapping
    fun myRepositoriesConnection(
        @ContextValue(name = "currentUser", required = false) user: CustomUserDetails?,
        @Argument first: Int?,
        @Argument after: String?,
        @Argument last: Int?,
        @Argument before: String?,
        @Argument sortBy: String?,
        @Argument sortDirection: String?,
        @Argument keyword: String?
    ): RepositoryListItemConnection {
        val currentUserId = user?.getUserId()
            ?: throw UserLoginException("USER_NOT_LOGGED_IN", "사용자가 로그인하지 않았습니다.")

        val paging = KeysetPaging(first = first, after = after, last = last, before = before)
        PagingValidator.validate(paging)

        return repositoryQueryService.getMyRepositoriesConnection(
            currentUserId = currentUserId,
            paging = paging,
            sortBy = sortBy ?: "updatedAt",
            sortDirection = sortDirection ?: "DESC",
            keyword = keyword
        )
    }

    @QueryMapping
    fun userPublicRepositories(
        @Argument userId: Long,
        @Argument first: Int?,
        @Argument after: String?,
        @Argument last: Int?,
        @Argument before: String?,
        @Argument sortBy: String?,
        @Argument sortDirection: String?,
        @Argument keyword: String?
    ): RepositoryListItemConnection {
        val paging = KeysetPaging(first = first, after = after, last = last, before = before)
        PagingValidator.validate(paging)

        return repositoryQueryService.getUserRepositoriesConnection(
            targetUserId = userId,
            currentUserId = null,
            paging = paging,
            sortBy = sortBy ?: "updatedAt",
            sortDirection = sortDirection ?: "DESC",
            keyword = keyword
        )
    }

    @QueryMapping
    fun userRepositoriesConnection(
        @Argument userId: Long,
        @Argument first: Int?,
        @Argument after: String?,
        @Argument last: Int?,
        @Argument before: String?,
        @Argument sortBy: String?,
        @Argument sortDirection: String?,
        @Argument keyword: String?,
        @ContextValue(name = "currentUser", required = false) user: CustomUserDetails?
    ): RepositoryListItemConnection {
        val currentUserId = user?.getUserId()
        val paging = KeysetPaging(first = first, after = after, last = last, before = before)
        PagingValidator.validate(paging)

        return repositoryQueryService.getUserRepositoriesConnection(
            targetUserId = userId,
            currentUserId = currentUserId,
            paging = paging,
            sortBy = sortBy ?: "updatedAt",
            sortDirection = sortDirection ?: "DESC",
            keyword = keyword
        )
    }
}
