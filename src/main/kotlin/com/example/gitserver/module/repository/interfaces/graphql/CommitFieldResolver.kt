package com.example.gitserver.module.repository.interfaces.graphql

import com.example.gitserver.config.graphql.UserByIdDataLoaderFactory
import com.example.gitserver.module.repository.interfaces.dto.CommitResponse
import com.example.gitserver.module.repository.interfaces.dto.RepositoryUserResponse
import graphql.schema.DataFetchingEnvironment
import org.dataloader.DataLoader
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller
import java.util.concurrent.CompletableFuture

@Controller
class CommitFieldResolver {

    @SchemaMapping(typeName = "CommitResponse", field = "author")
    fun author(
        commit: CommitResponse,
        env: DataFetchingEnvironment
    ): CompletableFuture<RepositoryUserResponse> {
        val loader: DataLoader<Long, RepositoryUserResponse>? =
            env.getDataLoader(UserByIdDataLoaderFactory.KEY)
        if (loader == null) {
            return CompletableFuture.completedFuture(commit.author)
        }

        return loader.load(commit.author.userId)
            .thenApply { it ?: commit.author }
    }

}
