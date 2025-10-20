package com.example.gitserver.config.graphql


import com.example.gitserver.module.gitindex.application.query.CommitQueryService
import com.example.gitserver.module.repository.interfaces.dto.CommitResponse
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderOptions
import org.dataloader.MappedBatchLoaderWithContext
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

/**
 * GraphQL N+1 완화를 위한 커밋 로더.
 * 키는 (repositoryId, commitHash) 복합키를 사용합니다.
 */
@Component
class CommitByHashDataLoaderFactory(
    private val commitQueryService: CommitQueryService
) {

    private val log = mu.KotlinLogging.logger {}

    data class Key(val repositoryId: Long, val hash: String)

    fun create(): DataLoader<Key, CommitResponse?> {
        val batchLoader = MappedBatchLoaderWithContext<Key, CommitResponse?> { keys, _ ->
            log.info { "CommitByHashDataLoader: batch load keys=${keys.joinToString()}" }
            CompletableFuture.supplyAsync {
                keys.distinct().associateWith { k ->
                    runCatching { commitQueryService.getCommitInfo(k.repositoryId, k.hash) }
                        .getOrNull()
                }
            }
        }

        val options = DataLoaderOptions.newOptions()
            .setCachingEnabled(true)
            .setMaxBatchSize(100)

        return DataLoaderFactory.newMappedDataLoader(batchLoader, options)
    }

    companion object {
        const val KEY = "COMMIT_BY_HASH"
    }
}
