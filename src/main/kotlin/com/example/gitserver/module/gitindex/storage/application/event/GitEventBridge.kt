package com.example.gitserver.module.gitindex.storage.application.event

import com.example.gitserver.module.gitindex.shared.domain.event.GitEvent
import com.example.gitserver.module.gitindex.storage.infrastructure.event.GitEventPublisher
import com.example.gitserver.common.util.LogContext
import com.example.gitserver.module.repository.domain.event.BranchCreated
import com.example.gitserver.module.repository.domain.event.BranchDeleted
import com.example.gitserver.module.repository.domain.event.CollaboratorAccepted
import com.example.gitserver.module.repository.domain.event.CollaboratorInvited
import com.example.gitserver.module.repository.domain.event.CollaboratorRejected
import com.example.gitserver.module.repository.domain.event.CollaboratorRemoved
import com.example.gitserver.module.repository.domain.event.RepositoryCreated
import com.example.gitserver.module.repository.domain.event.RepositoryDeleted
import com.example.gitserver.module.repository.domain.event.RepositoryRenamed
import com.example.gitserver.module.repository.domain.event.RepositoryPushed
import com.example.gitserver.module.repository.domain.event.RepositoryVisibilityChanged
import com.example.gitserver.module.repository.domain.event.RepositoryStarred
import com.example.gitserver.module.repository.domain.event.RepositoryUnstarred
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import mu.KotlinLogging
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.context.annotation.Profile

@Component
@Profile("gitstorage")
class GitEventBridge(
    private val gitEventPublisher: GitEventPublisher,
    private val repositoryRepository: RepositoryRepository
) {
    private val log = KotlinLogging.logger {}

    @EventListener
    fun onRepositoryCreated(e: RepositoryCreated) {
        LogContext.with("eventType" to "REPO_CREATED", "repoId" to e.repositoryId.toString()) {
            val event = GitEvent(
                eventType = "REPO_CREATED",
                repositoryId = e.repositoryId,
                ownerId = e.ownerId,
                name = e.name,
                branch = null
            )
            log.info { "[GitEventBridge] RepositoryCreated -> GitEvent" }
            gitEventPublisher.publish(event)
        }
    }

    @EventListener
    fun onRepositoryPushed(e: RepositoryPushed) {
        LogContext.with(
            "eventType" to "PUSH",
            "repoId" to e.repositoryId.toString(),
            "branch" to e.branch
        ) {
            val event = GitEvent(
                eventType = "PUSH",
                repositoryId = e.repositoryId,
                ownerId = e.ownerId,
                name = e.name,
                branch = e.branch,
                oldrev = e.oldrev,
                newrev = e.newrev,
                actorId = e.actorId
            )
            log.info { "[GitEventBridge] RepositoryPushed -> GitEvent" }
            gitEventPublisher.publish(event)
        }
    }

    @EventListener
    fun onBranchCreated(e: BranchCreated) {
        val repo = repositoryRepository.findById(e.repositoryId).orElse(null)
        if (repo == null) {
            log.warn { "[GitEventBridge] BranchCreated skipped: repo not found id=${e.repositoryId}" }
            return
        }

        LogContext.with(
            "eventType" to "BRANCH_CREATED",
            "repoId" to e.repositoryId.toString(),
            "branch" to e.fullRef
        ) {
            val event = GitEvent(
                eventType = "BRANCH_CREATED",
                repositoryId = e.repositoryId,
                ownerId = repo.owner.id,
                name = repo.name,
                branch = e.fullRef,
                oldrev = null,
                newrev = e.headCommit
            )
            log.info { "[GitEventBridge] BranchCreated -> GitEvent" }
            gitEventPublisher.publish(event)
        }
    }

    @EventListener
    fun onBranchDeleted(e: BranchDeleted) {
        val repo = repositoryRepository.findById(e.repositoryId).orElse(null)
        if (repo == null) {
            log.warn { "[GitEventBridge] BranchDeleted skipped: repo not found id=${e.repositoryId}" }
            return
        }

        LogContext.with(
            "eventType" to "BRANCH_DELETED",
            "repoId" to e.repositoryId.toString(),
            "branch" to e.fullRef
        ) {
            val event = GitEvent(
                eventType = "BRANCH_DELETED",
                repositoryId = e.repositoryId,
                ownerId = repo.owner.id,
                name = repo.name,
                branch = e.fullRef,
                oldrev = null,
                newrev = "0000000000000000000000000000000000000000"
            )
            log.info { "[GitEventBridge] BranchDeleted -> GitEvent" }
            gitEventPublisher.publish(event)
        }
    }

    @EventListener
    fun onRepositoryRenamed(e: RepositoryRenamed) {
        val repo = repositoryRepository.findById(e.repositoryId).orElse(null)
        if (repo == null) {
            log.warn { "[GitEventBridge] RepositoryRenamed skipped: repo not found id=${e.repositoryId}" }
            return
        }

        LogContext.with(
            "eventType" to "REPO_RENAMED",
            "repoId" to e.repositoryId.toString()
        ) {
            val event = GitEvent(
                eventType = "REPO_RENAMED",
                repositoryId = e.repositoryId,
                ownerId = repo.owner.id,
                name = repo.name,
                branch = null,
                oldName = e.oldName,
                newName = e.newName
            )
            log.info { "[GitEventBridge] RepositoryRenamed -> GitEvent" }
            gitEventPublisher.publish(event)
        }
    }

    @EventListener
    fun onRepositoryDeleted(e: RepositoryDeleted) {
        val repo = repositoryRepository.findById(e.repositoryId).orElse(null)
        if (repo == null) {
            log.warn { "[GitEventBridge] RepositoryDeleted skipped: repo not found id=${e.repositoryId}" }
            return
        }

        LogContext.with(
            "eventType" to "REPO_DELETED",
            "repoId" to e.repositoryId.toString()
        ) {
            val event = GitEvent(
                eventType = "REPO_DELETED",
                repositoryId = e.repositoryId,
                ownerId = repo.owner.id,
                name = repo.name,
                branch = null
            )
            log.info { "[GitEventBridge] RepositoryDeleted -> GitEvent" }
            gitEventPublisher.publish(event)
        }
    }

    @EventListener
    fun onRepositoryVisibilityChanged(e: RepositoryVisibilityChanged) {
        val repo = repositoryRepository.findById(e.repositoryId).orElse(null)
        if (repo == null) {
            log.warn { "[GitEventBridge] RepositoryVisibilityChanged skipped: repo not found id=${e.repositoryId}" }
            return
        }

        LogContext.with(
            "eventType" to "REPO_VISIBILITY_CHANGED",
            "repoId" to e.repositoryId.toString()
        ) {
            val event = GitEvent(
                eventType = "REPO_VISIBILITY_CHANGED",
                repositoryId = e.repositoryId,
                ownerId = repo.owner.id,
                name = repo.name,
                branch = null,
                visibilityCode = e.visibilityCode
            )
            log.info { "[GitEventBridge] RepositoryVisibilityChanged -> GitEvent" }
            gitEventPublisher.publish(event)
        }
    }

    @EventListener
    fun onCollaboratorInvited(e: CollaboratorInvited) {
        val repo = repositoryRepository.findById(e.repositoryId).orElse(null)
        if (repo == null) {
            log.warn { "[GitEventBridge] CollaboratorInvited skipped: repo not found id=${e.repositoryId}" }
            return
        }

        LogContext.with(
            "eventType" to "COLLAB_INVITED",
            "repoId" to e.repositoryId.toString()
        ) {
            val event = GitEvent(
                eventType = "COLLAB_INVITED",
                repositoryId = e.repositoryId,
                ownerId = repo.owner.id,
                name = repo.name,
                branch = null,
                collaboratorUserId = e.userId
            )
            log.info { "[GitEventBridge] CollaboratorInvited -> GitEvent" }
            gitEventPublisher.publish(event)
        }
    }

    @EventListener
    fun onCollaboratorAccepted(e: CollaboratorAccepted) {
        val repo = repositoryRepository.findById(e.repositoryId).orElse(null)
        if (repo == null) {
            log.warn { "[GitEventBridge] CollaboratorAccepted skipped: repo not found id=${e.repositoryId}" }
            return
        }

        LogContext.with(
            "eventType" to "COLLAB_ACCEPTED",
            "repoId" to e.repositoryId.toString()
        ) {
            val event = GitEvent(
                eventType = "COLLAB_ACCEPTED",
                repositoryId = e.repositoryId,
                ownerId = repo.owner.id,
                name = repo.name,
                branch = null,
                collaboratorUserId = e.userId
            )
            log.info { "[GitEventBridge] CollaboratorAccepted -> GitEvent" }
            gitEventPublisher.publish(event)
        }
    }

    @EventListener
    fun onCollaboratorRejected(e: CollaboratorRejected) {
        val repo = repositoryRepository.findById(e.repositoryId).orElse(null)
        if (repo == null) {
            log.warn { "[GitEventBridge] CollaboratorRejected skipped: repo not found id=${e.repositoryId}" }
            return
        }

        LogContext.with(
            "eventType" to "COLLAB_REJECTED",
            "repoId" to e.repositoryId.toString()
        ) {
            val event = GitEvent(
                eventType = "COLLAB_REJECTED",
                repositoryId = e.repositoryId,
                ownerId = repo.owner.id,
                name = repo.name,
                branch = null,
                collaboratorUserId = e.userId
            )
            log.info { "[GitEventBridge] CollaboratorRejected -> GitEvent" }
            gitEventPublisher.publish(event)
        }
    }

    @EventListener
    fun onCollaboratorRemoved(e: CollaboratorRemoved) {
        val repo = repositoryRepository.findById(e.repositoryId).orElse(null)
        if (repo == null) {
            log.warn { "[GitEventBridge] CollaboratorRemoved skipped: repo not found id=${e.repositoryId}" }
            return
        }

        LogContext.with(
            "eventType" to "COLLAB_REMOVED",
            "repoId" to e.repositoryId.toString()
        ) {
            val event = GitEvent(
                eventType = "COLLAB_REMOVED",
                repositoryId = e.repositoryId,
                ownerId = repo.owner.id,
                name = repo.name,
                branch = null,
                collaboratorUserId = e.userId
            )
            log.info { "[GitEventBridge] CollaboratorRemoved -> GitEvent" }
            gitEventPublisher.publish(event)
        }
    }

    @EventListener
    fun onRepositoryStarred(e: RepositoryStarred) {
        val repo = repositoryRepository.findById(e.repositoryId).orElse(null)
        if (repo == null) {
            log.warn { "[GitEventBridge] RepositoryStarred skipped: repo not found id=${e.repositoryId}" }
            return
        }

        LogContext.with(
            "eventType" to "REPO_STARRED",
            "repoId" to e.repositoryId.toString()
        ) {
            val event = GitEvent(
                eventType = "REPO_STARRED",
                repositoryId = e.repositoryId,
                ownerId = repo.owner.id,
                name = repo.name,
                branch = null,
                actorId = e.userId
            )
            log.info { "[GitEventBridge] RepositoryStarred -> GitEvent" }
            gitEventPublisher.publish(event)
        }
    }

    @EventListener
    fun onRepositoryUnstarred(e: RepositoryUnstarred) {
        val repo = repositoryRepository.findById(e.repositoryId).orElse(null)
        if (repo == null) {
            log.warn { "[GitEventBridge] RepositoryUnstarred skipped: repo not found id=${e.repositoryId}" }
            return
        }

        LogContext.with(
            "eventType" to "REPO_UNSTARRED",
            "repoId" to e.repositoryId.toString()
        ) {
            val event = GitEvent(
                eventType = "REPO_UNSTARRED",
                repositoryId = e.repositoryId,
                ownerId = repo.owner.id,
                name = repo.name,
                branch = null,
                actorId = e.userId
            )
            log.info { "[GitEventBridge] RepositoryUnstarred -> GitEvent" }
            gitEventPublisher.publish(event)
        }
    }
}
