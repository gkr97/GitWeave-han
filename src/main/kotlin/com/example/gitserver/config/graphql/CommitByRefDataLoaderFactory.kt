package com.example.gitserver.config.graphql

import com.example.gitserver.module.gitindex.indexer.application.query.CommitQueryService
import com.example.gitserver.module.repository.interfaces.dto.CommitResponse
import com.example.gitserver.common.util.LogContext
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.MappedBatchLoaderWithContext
import org.dataloader.DataLoaderOptions
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ForkJoinPool

/**
 * Key: (repositoryId, ref or fullRef)
 * Value: CommitResponse (브랜치/태그의 최신 커밋)
 */
@Component
class CommitByRefDataLoaderFactory(
    private val commitQueryService: CommitQueryService
) {
    private val log = mu.KotlinLogging.logger {}

    data class RefKey(val repositoryId: Long, val ref: String)

    fun create(): DataLoader<RefKey, CommitResponse?> {
        val batchLoader = MappedBatchLoaderWithContext<RefKey, CommitResponse?> { keys, _ ->
            log.info { "CommitByRefDataLoader: batch load keys=${keys.joinToString()}" }
            CompletableFuture.supplyAsync({
                val byRepo = keys.groupBy { it.repositoryId }
                val result = HashMap<RefKey, CommitResponse?>(keys.size)

                byRepo.forEach { (repoId, group) ->
                    val refs = group.map { it.ref }
                    val map  = commitQueryService
                        .getLatestCommitHashBatch(repoId, refs)
                    group.forEach { k -> result[k] = map[k.ref] }
                }
                result
            }, LogContext.wrappingExecutor(ForkJoinPool.commonPool()))
        }

        val options = DataLoaderOptions.newOptions()
            .setCachingEnabled(true)
            .setMaxBatchSize(200)
        return DataLoaderFactory.newMappedDataLoader(batchLoader, options)
    }

    companion object { const val KEY = "COMMIT_BY_REF" }
}
