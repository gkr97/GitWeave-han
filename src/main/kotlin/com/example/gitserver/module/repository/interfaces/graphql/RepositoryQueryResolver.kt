package com.example.gitserver.module.repository.interfaces.graphql

import com.example.gitserver.common.pagination.KeysetPaging
import com.example.gitserver.common.pagination.PagingValidator
import com.example.gitserver.module.repository.application.query.RepositoryQueryService
import com.example.gitserver.module.repository.application.query.model.RepositoryListItemConnection
import com.example.gitserver.module.repository.interfaces.dto.*
import com.example.gitserver.module.user.domain.CustomUserDetails
import com.example.gitserver.module.user.exception.UserLoginException
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.BindException
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
        @AuthenticationPrincipal user: CustomUserDetails?
    ): RepoDetailResponse =
        repositoryQueryService.getRepository(id, branch, user?.getUserId())

    @QueryMapping
    fun myRepositoriesConnection(
        @AuthenticationPrincipal user: CustomUserDetails?,
        @Argument first: Int?,
        @Argument after: String?,
        @Argument last: Int?,
        @Argument before: String?,
        @Argument sortBy: String?,
        @Argument sortDirection: String?,
        @Argument keyword: String?
    ): RepositoryListItemConnection {
        val userId = user?.getUserId()
            ?: throw UserLoginException("USER_NOT_LOGGED_IN", "사용자가 로그인하지 않았습니다.")

        val paging = KeysetPaging(first = first, after = after, last = last, before = before)
        PagingValidator.validate(paging)

        return repositoryQueryService.getMyRepositoriesConnection(
            currentUserId = userId,
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
        @AuthenticationPrincipal user: CustomUserDetails?
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
