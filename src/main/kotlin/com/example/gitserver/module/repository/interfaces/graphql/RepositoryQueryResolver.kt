package com.example.gitserver.module.repository.interfaces.graphql

import com.example.gitserver.module.repository.application.query.RepositoryQueryService
import com.example.gitserver.module.repository.interfaces.dto.MyRepositoriesResult
import com.example.gitserver.module.repository.interfaces.dto.RepoDetailResponse
import com.example.gitserver.module.repository.interfaces.dto.RepositoryListRequest
import com.example.gitserver.module.user.domain.CustomUserDetails
import com.example.gitserver.module.user.exception.UserLoginException
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

    @QueryMapping
    fun myRepositories(
        @AuthenticationPrincipal user: CustomUserDetails?,
        @Argument request: RepositoryListRequest?
    ): MyRepositoriesResult {
        val userId = user?.getUserId() ?: throw UserLoginException("USER_NOT_LOGGED_IN", "사용자가 로그인하지 않았습니다.")
        val requestActual = request ?: RepositoryListRequest()
        return repositoryQueryService.getRepositoryList(userId, requestActual)
    }


}