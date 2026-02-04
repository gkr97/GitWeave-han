package com.example.gitserver.module.repository.interfaces.graphql

import com.example.gitserver.module.repository.application.query.RepositoryFileQueryService
import com.example.gitserver.module.repository.interfaces.dto.FileExplorerResponse
import graphql.schema.DataFetchingEnvironment
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller

@Controller
class RepositoryFileExplorerQueryResolver(
    private val repositoryFileQueryService: RepositoryFileQueryService
) {

    @QueryMapping
    fun repositoryFileExplorer(
        @Argument repositoryId: Long,
        @Argument treePath: String?,
        @Argument filePath: String?,
        @Argument branch: String?,
        @Argument commitHash: String?,
        env: DataFetchingEnvironment
    ): FileExplorerResponse {
        val userId = env.graphQlContext.get<Long?>("currentUserId")
        val result = repositoryFileQueryService.getFileExplorer(
            repositoryId,
            treePath,
            filePath,
            branch,
            commitHash,
            userId
        )
        env.graphQlContext.put("fileExplorerResponse", result)
        return result
    }
}
