package com.example.gitserver.module.repository.interfaces.graphql

import com.example.gitserver.module.repository.application.query.RepositoryFileQueryService
import com.example.gitserver.module.repository.interfaces.dto.FileContentResponse
import com.example.gitserver.module.repository.interfaces.dto.TreeNodeResponse
import com.example.gitserver.module.user.domain.CustomUserDetails
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.ContextValue
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

@Controller
class RepositoryFileQueryResolver(
    private val repositoryFileQueryService: RepositoryFileQueryService
) {

    @SchemaMapping(typeName = "RepositoryResponse", field = "fileTree")
    fun fileTree(
        source: com.example.gitserver.module.repository.interfaces.dto.RepoDetailResponse,
        @Argument commitHash: String?,
        @Argument path: String?,
        @Argument branch: String?,
        @ContextValue(name = "currentUser", required = false) user: CustomUserDetails?
    ): List<TreeNodeResponse> {
        return repositoryFileQueryService.getFileTree(
            source.id, commitHash, path, branch, user?.getUserId()
        )
    }

    @SchemaMapping(typeName = "RepositoryResponse", field = "fileContent")
    fun fileContent(
        source: com.example.gitserver.module.repository.interfaces.dto.RepoDetailResponse,
        @Argument commitHash: String?,
        @Argument path: String,
        @Argument branch: String?,
        @ContextValue(name = "currentUser", required = false) user: CustomUserDetails?
    ): FileContentResponse {
        return repositoryFileQueryService.getFileContent(
            source.id, commitHash, path, branch, user?.getUserId()
        )
    }
}
