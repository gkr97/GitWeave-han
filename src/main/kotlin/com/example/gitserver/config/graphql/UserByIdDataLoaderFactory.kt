package com.example.gitserver.config.graphql

import com.example.gitserver.module.repository.interfaces.dto.RepositoryUserResponse
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import com.example.gitserver.common.util.LogContext
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderOptions
import org.dataloader.MappedBatchLoaderWithContext
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ForkJoinPool


/**
 * GraphQL N+1 완화를 위한 유저 로더.
 * 키는 userId (Long) 단일키를 사용합니다.
 */
@Component
class UserByIdDataLoaderFactory(
    private val userRepository: UserRepository
) {

    private val log = mu.KotlinLogging.logger {}

    fun create(): DataLoader<Long, RepositoryUserResponse> {
        val batchLoader = MappedBatchLoaderWithContext<Long, RepositoryUserResponse> { keys, _ ->
            log.info { "UserByIdDataLoader: batch load user ids=${keys.joinToString()}" }
            CompletableFuture.supplyAsync({
                val users = userRepository.findAllById(keys).toList()
                users.associateBy({ it.id }) { u ->
                    RepositoryUserResponse(
                        userId = u.id,
                        nickname = u.name ?: "알 수 없음",
                        profileImageUrl = u.profileImageUrl
                    )
                }
            }, LogContext.wrappingExecutor(ForkJoinPool.commonPool()))
        }

        val options = DataLoaderOptions.newOptions()
            .setCachingEnabled(true)

        return DataLoaderFactory.newMappedDataLoader(batchLoader, options)
    }

    companion object {
        const val KEY = "USER_BY_ID"
    }
}
