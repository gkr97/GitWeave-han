package com.example.gitserver.module.repository.interfaces.graphql

import com.example.gitserver.config.graphql.CommitByRefDataLoaderFactory
import com.example.gitserver.config.graphql.CommitByRefDataLoaderFactory.RefKey
import com.example.gitserver.module.repository.interfaces.dto.BranchResponse
import com.example.gitserver.module.repository.interfaces.dto.CommitResponse
import graphql.schema.DataFetchingEnvironment
import org.dataloader.DataLoader
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller
import java.util.concurrent.CompletableFuture

@Controller
class BranchFieldResolver {

    @SchemaMapping(typeName = "BranchResponse", field = "headCommit")
    fun headCommit(
        branch: BranchResponse,
        env: DataFetchingEnvironment
    ): CompletableFuture<CommitResponse> {

        val loader: DataLoader<RefKey, CommitResponse?>? =
            env.getDataLoader(CommitByRefDataLoaderFactory.KEY)

        if (loader == null) {
            return CompletableFuture.completedFuture(branch.headCommit)
        }

        val key = RefKey(branch._repositoryId, branch.qualifiedName)
        return loader.load(key).thenApply { it ?: branch.headCommit }
    }
}
