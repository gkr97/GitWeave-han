package com.example.gitserver.module.repository.interfaces.graphql

import com.example.gitserver.module.repository.application.query.RepositoryQueryService
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
    ): RepoDetailResponse {
        return repositoryQueryService.getRepository(id, branch, user?.getUserId())
    }

    @QueryMapping
    fun myRepositories(
        @AuthenticationPrincipal user: CustomUserDetails?,
        @Argument page: Int?,
        @Argument size: Int?,
        @Argument sortBy: String?,
        @Argument sortDirection: String?,
        @Argument keyword: String?
    ): MyRepositoriesResponse {
        val userId = user?.getUserId() ?: throw UserLoginException("USER_NOT_LOGGED_IN", "사용자가 로그인하지 않았습니다.")
        val request = RepositoryListRequest(
            page = page ?: 1,
            size = size ?: 10,
            sortBy = sortBy ?: "lastUpdatedAt",
            sortDirection = sortDirection ?: "DESC",
            keyword = keyword
        )

        val errors = BeanPropertyBindingResult(request, "repositoryListRequest")
        validator.validate(request, errors)
        if (errors.hasErrors()) {
            throw BindException(errors)
        }

        return repositoryQueryService.getRepositoryList(userId, request)
    }

    @QueryMapping
    fun userRepositories(
        @Argument userId: Long,
        @Argument page: Int?,
        @Argument size: Int?,
        @Argument sortBy: String?,
        @Argument sortDirection: String?,
        @Argument keyword: String?,
        @AuthenticationPrincipal user: CustomUserDetails?
    ): UserRepositoriesResult {
        val currentUserId = user?.getUserId()
        val request = UserRepositoryListRequest(
            page = page ?: 1,
            size = size ?: 10,
            sortBy = sortBy ?: "lastUpdatedAt",
            sortDirection = sortDirection ?: "DESC",
            keyword = keyword
        )

        val error = BeanPropertyBindingResult(request, "userRepositoryListRequest")
        validator.validate(request, error)
        if (error.hasErrors()) {
            throw BindException(error)
        }

        return repositoryQueryService.getUserRepositoryList(userId, currentUserId, request)
    }
}
