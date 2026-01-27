package com.example.gitserver.module.search.infrastructure.opensearch

import com.example.gitserver.common.resilience.ResilienceUtils
import com.example.gitserver.module.repository.domain.Repository
import com.example.gitserver.module.repository.exception.RepositoryNotFoundException
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryStatsRepository
import com.example.gitserver.module.search.domain.RepositoryDoc
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryRegistry
import mu.KotlinLogging
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch.core.BulkRequest
import org.opensearch.client.opensearch.core.DeleteRequest
import org.opensearch.client.opensearch.core.IndexRequest
import org.opensearch.client.opensearch.core.bulk.BulkOperation
import org.springframework.stereotype.Component
import java.time.ZoneOffset

@Component
class RepositorySearchSync(
    private val osClient: OpenSearchClient,
    private val topicRepo: RepositoryTopicRepository,
    private val statsJpaRepo: RepositoryStatsRepository,
    private val repositoryJpaRepo: RepositoryRepository,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
    private val retryRegistry: RetryRegistry
) {
    private val mapper = jacksonObjectMapper()
    private val log = KotlinLogging.logger {}

    fun indexRepository(repo: Repository) {
        val repoId = repo.id
        if (repoId == 0L) throw RepositoryNotFoundException(0L)

        val topics = runCatching { topicRepo.findTopicsByRepositoryId(repoId) }.getOrDefault(emptyList())
        val stats = runCatching { statsJpaRepo.findById(repoId).orElse(null) }.getOrNull()

        val lastCommitAtIso = stats?.lastCommitAt?.atOffset(ZoneOffset.UTC)?.toString()
        val createdAtIso = try {
            repo.createdAt.atOffset(ZoneOffset.UTC).toString()
        } catch (_: Exception) {
            repo.createdAt.toString()
        }
        val updatedAtIso = repo.updatedAt?.let {
            try { it.atOffset(ZoneOffset.UTC).toString() } catch (_: Exception) { it.toString() }
        }

        val doc = RepositoryDoc(
            id = repoId,
            owner_id = repo.owner.id,
            name = repo.name,
            description = repo.description,
            topics = topics,
            language = repo.language,
            default_branch = repo.defaultBranch,
            stars = stats?.stars ?: 0,
            forks = stats?.forks ?: 0,
            watchers = stats?.watchers ?: 0,
            issues = stats?.issues ?: 0,
            pull_requests = stats?.pullRequests ?: 0,
            last_commit_at = lastCommitAtIso,
            created_at = createdAtIso,
            updated_at = updatedAtIso
        )

        ResilienceUtils.executeWithResilience(
            circuitBreakerName = "opensearch",
            retryName = "opensearch",
            circuitBreakerRegistry = circuitBreakerRegistry,
            retryRegistry = retryRegistry
        ) {
            val req = IndexRequest.Builder<RepositoryDoc>()
                .index("repositories")
                .id(repoId.toString())
                .document(doc)
                .build()
            osClient.index(req)
            log.info { "OpenSearch indexed repository id=${repoId}" }
        }
    }

    fun bulkIndex(repos: List<Repository>) {
        if (repos.isEmpty()) return
        val ops = repos.map { repo ->
            val repoId = repo.id
            val topics = runCatching { topicRepo.findTopicsByRepositoryId(repoId) }.getOrDefault(emptyList())
            val stats = runCatching { statsJpaRepo.findById(repoId).orElse(null) }.getOrNull()

            val doc = RepositoryDoc(
                id = repoId,
                owner_id = repo.owner.id,
                name = repo.name,
                description = repo.description,
                topics = topics,
                language = repo.language,
                default_branch = repo.defaultBranch,
                stars = stats?.stars ?: 0,
                forks = stats?.forks ?: 0,
                watchers = stats?.watchers ?: 0,
                issues = stats?.issues ?: 0,
                pull_requests = stats?.pullRequests ?: 0,
                last_commit_at = stats?.lastCommitAt?.atOffset(ZoneOffset.UTC)?.toString(),
                created_at = try { repo.createdAt.atOffset(ZoneOffset.UTC).toString() } catch (_: Exception) { repo.createdAt.toString() },
                updated_at = repo.updatedAt?.let { try { it.atOffset(ZoneOffset.UTC).toString() } catch (_: Exception) { it.toString() } }
            )

            BulkOperation.Builder()
                .index { b -> b.index("repositories").id(repoId.toString()).document(doc) }
                .build()
        }


        val bulkReq = BulkRequest.Builder().operations(ops).build()
        ResilienceUtils.executeWithResilience(
            circuitBreakerName = "opensearch",
            retryName = "opensearch",
            circuitBreakerRegistry = circuitBreakerRegistry,
            retryRegistry = retryRegistry
        ) {
            osClient.bulk(bulkReq)
            log.info { "OpenSearch bulk indexed ${repos.size} repositories" }
        }
    }

    fun indexRepositoryById(repositoryId: Long) {
        val repo = repositoryJpaRepo.findById(repositoryId).orElse(null) 
            ?: throw RepositoryNotFoundException(repositoryId)
        indexRepository(repo)
    }

    fun deleteRepositoryById(repositoryId: Long) {
        ResilienceUtils.executeWithResilience(
            circuitBreakerName = "opensearch",
            retryName = "opensearch",
            circuitBreakerRegistry = circuitBreakerRegistry,
            retryRegistry = retryRegistry
        ) {
            val req = DeleteRequest.Builder()
                .index("repositories")
                .id(repositoryId.toString())
                .build()
            osClient.delete(req)
            log.info { "OpenSearch deleted repository id=$repositoryId" }
        }
    }
}