package com.example.gitserver.module.repository.interfaces.graphql

import com.example.gitserver.common.util.GitRefUtils
import com.example.gitserver.module.repository.application.query.RepositoryFileQueryService
import com.example.gitserver.module.repository.interfaces.dto.FileContentResponse
import com.example.gitserver.module.repository.interfaces.dto.TreeNodeResponse
import com.example.gitserver.module.user.domain.CustomUserDetails
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller

@Controller
class RepositoryFileQueryResolver(
    private val repositoryFileQueryService: RepositoryFileQueryService
) {

    @QueryMapping
    fun repositoryFileTree(
        @Argument repositoryId: Long,
        @Argument commitHash: String?,
        @Argument path: String?,
        @Argument branch: String?,
        @AuthenticationPrincipal user: CustomUserDetails?
    ): List<TreeNodeResponse> {
        return repositoryFileQueryService.getFileTree(
            repositoryId, commitHash, path, branch, user?.getUserId()
        )
    }

    @QueryMapping
    fun repositoryFileContent(
        @Argument repositoryId: Long,
        @Argument commitHash: String?,
        @Argument path: String,
        @Argument branch: String?,
        @AuthenticationPrincipal user: CustomUserDetails?
    ): FileContentResponse {
        return repositoryFileQueryService.getFileContent(
            repositoryId, commitHash, path, branch, user?.getUserId()
        )
    }
}
