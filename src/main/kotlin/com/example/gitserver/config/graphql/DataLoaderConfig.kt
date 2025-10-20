package com.example.gitserver.config.graphql

import graphql.GraphQLContext
import org.dataloader.DataLoaderRegistry
import org.springframework.context.annotation.Configuration
import org.springframework.graphql.execution.DataLoaderRegistrar

/**
 * DataLoader 등록
 */
@Configuration
class DataLoaderConfig(
    private val userLoaderFactory: UserByIdDataLoaderFactory,
    private val commitByHashLoaderFactory: CommitByHashDataLoaderFactory,
    private val commitByRefLoaderFactory: CommitByRefDataLoaderFactory,
) : DataLoaderRegistrar {

    override fun registerDataLoaders(registry: DataLoaderRegistry, context: GraphQLContext) {
        registry.register(UserByIdDataLoaderFactory.KEY, userLoaderFactory.create())
        registry.register(CommitByHashDataLoaderFactory.KEY, commitByHashLoaderFactory.create())
        registry.register(CommitByRefDataLoaderFactory.KEY, commitByRefLoaderFactory.create())
    }
}

