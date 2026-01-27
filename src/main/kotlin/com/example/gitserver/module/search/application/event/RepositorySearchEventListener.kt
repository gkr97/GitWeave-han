package com.example.gitserver.module.search.application.event

import com.example.gitserver.module.repository.domain.event.RepositoryCreated
import com.example.gitserver.module.repository.domain.event.RepositoryDeleted
import com.example.gitserver.module.repository.domain.event.RepositoryRenamed
import com.example.gitserver.module.repository.domain.event.RepositoryStarred
import com.example.gitserver.module.repository.domain.event.RepositoryUnstarred
import com.example.gitserver.module.repository.domain.event.RepositoryVisibilityChanged
import com.example.gitserver.module.search.infrastructure.opensearch.RepositorySearchSync
import mu.KotlinLogging
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class RepositorySearchEventListener(
    private val searchSync: RepositorySearchSync
) {
    private val log = KotlinLogging.logger {}

    @EventListener
    fun onRepositoryCreated(e: RepositoryCreated) {
        runCatching { searchSync.indexRepositoryById(e.repositoryId) }
            .onFailure { ex -> log.warn(ex) { "[SearchSync] RepositoryCreated index failed repoId=${e.repositoryId}" } }
    }

    @EventListener
    fun onRepositoryRenamed(e: RepositoryRenamed) {
        runCatching { searchSync.indexRepositoryById(e.repositoryId) }
            .onFailure { ex -> log.warn(ex) { "[SearchSync] RepositoryRenamed index failed repoId=${e.repositoryId}" } }
    }

    @EventListener
    fun onRepositoryVisibilityChanged(e: RepositoryVisibilityChanged) {
        runCatching { searchSync.indexRepositoryById(e.repositoryId) }
            .onFailure { ex -> log.warn(ex) { "[SearchSync] RepositoryVisibilityChanged index failed repoId=${e.repositoryId}" } }
    }

    @EventListener
    fun onRepositoryStarred(e: RepositoryStarred) {
        runCatching { searchSync.indexRepositoryById(e.repositoryId) }
            .onFailure { ex -> log.warn(ex) { "[SearchSync] RepositoryStarred index failed repoId=${e.repositoryId}" } }
    }

    @EventListener
    fun onRepositoryUnstarred(e: RepositoryUnstarred) {
        runCatching { searchSync.indexRepositoryById(e.repositoryId) }
            .onFailure { ex -> log.warn(ex) { "[SearchSync] RepositoryUnstarred index failed repoId=${e.repositoryId}" } }
    }

    @EventListener
    fun onRepositoryDeleted(e: RepositoryDeleted) {
        runCatching { searchSync.deleteRepositoryById(e.repositoryId) }
            .onFailure { ex -> log.warn(ex) { "[SearchSync] RepositoryDeleted delete failed repoId=${e.repositoryId}" } }
    }
}

