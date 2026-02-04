package com.example.gitserver.module.search.application.event

import com.example.gitserver.module.repository.domain.event.RepositoryCreated
import com.example.gitserver.module.repository.domain.event.RepositoryDeleted
import com.example.gitserver.module.repository.domain.event.RepositoryRenamed
import com.example.gitserver.module.repository.domain.event.RepositoryStarred
import com.example.gitserver.module.repository.domain.event.RepositoryUnstarred
import com.example.gitserver.module.repository.domain.event.RepositoryVisibilityChanged
import com.example.gitserver.module.search.infrastructure.opensearch.RepositorySearchSync
import com.example.gitserver.common.util.LogContext
import mu.KotlinLogging
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.context.annotation.Profile

@Component
@Profile("gitindexer")
class RepositorySearchEventListener(
    private val searchSync: RepositorySearchSync
) {
    private val log = KotlinLogging.logger {}

    @EventListener
    fun onRepositoryCreated(e: RepositoryCreated) {
        LogContext.with("eventType" to "REPO_CREATED", "repoId" to e.repositoryId.toString()) {
            runCatching { searchSync.indexRepositoryById(e.repositoryId) }
                .onFailure { ex -> log.warn(ex) { "[SearchSync] RepositoryCreated index failed" } }
        }
    }

    @EventListener
    fun onRepositoryRenamed(e: RepositoryRenamed) {
        LogContext.with("eventType" to "REPO_RENAMED", "repoId" to e.repositoryId.toString()) {
            runCatching { searchSync.indexRepositoryById(e.repositoryId) }
                .onFailure { ex -> log.warn(ex) { "[SearchSync] RepositoryRenamed index failed" } }
        }
    }

    @EventListener
    fun onRepositoryVisibilityChanged(e: RepositoryVisibilityChanged) {
        LogContext.with("eventType" to "REPO_VISIBILITY_CHANGED", "repoId" to e.repositoryId.toString()) {
            runCatching { searchSync.indexRepositoryById(e.repositoryId) }
                .onFailure { ex -> log.warn(ex) { "[SearchSync] RepositoryVisibilityChanged index failed" } }
        }
    }

    @EventListener
    fun onRepositoryStarred(e: RepositoryStarred) {
        LogContext.with("eventType" to "REPO_STARRED", "repoId" to e.repositoryId.toString()) {
            runCatching { searchSync.indexRepositoryById(e.repositoryId) }
                .onFailure { ex -> log.warn(ex) { "[SearchSync] RepositoryStarred index failed" } }
        }
    }

    @EventListener
    fun onRepositoryUnstarred(e: RepositoryUnstarred) {
        LogContext.with("eventType" to "REPO_UNSTARRED", "repoId" to e.repositoryId.toString()) {
            runCatching { searchSync.indexRepositoryById(e.repositoryId) }
                .onFailure { ex -> log.warn(ex) { "[SearchSync] RepositoryUnstarred index failed" } }
        }
    }

    @EventListener
    fun onRepositoryDeleted(e: RepositoryDeleted) {
        LogContext.with("eventType" to "REPO_DELETED", "repoId" to e.repositoryId.toString()) {
            runCatching { searchSync.deleteRepositoryById(e.repositoryId) }
                .onFailure { ex -> log.warn(ex) { "[SearchSync] RepositoryDeleted delete failed" } }
        }
    }
}
