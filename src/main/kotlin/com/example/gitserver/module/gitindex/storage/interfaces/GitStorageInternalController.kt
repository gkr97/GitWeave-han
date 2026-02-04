package com.example.gitserver.module.gitindex.storage.interfaces

import com.example.gitserver.module.gitindex.shared.domain.port.GitRepositoryPort
import com.example.gitserver.module.repository.application.command.handler.GitRepositorySyncHandler
import com.example.gitserver.module.repository.domain.event.SyncBranchEvent
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import org.springframework.context.annotation.Profile

@RestController
@RequestMapping("/internal/git/storage")
@Profile("gitstorage")
class GitStorageInternalController(
    private val repositoryRepository: RepositoryRepository,
    private val gitRepositoryPort: GitRepositoryPort,
    private val gitRepositorySyncHandler: GitRepositorySyncHandler
) {
    private val log = KotlinLogging.logger {}

    @PostMapping("/init")
    fun initRepository(@RequestBody req: GitStorageInitRequest): ResponseEntity<Void> {
        val repo = repositoryRepository.findById(req.repositoryId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        return try {
            gitRepositoryPort.initEmptyGitDirectory(
                repo,
                initializeReadme = req.initializeReadme,
                gitignoreTemplate = req.gitignoreTemplate,
                licenseTemplate = req.licenseTemplate
            )
            val headCommitHash = gitRepositoryPort.getHeadCommitHash(repo, req.defaultBranch)
            gitRepositorySyncHandler.handle(
                SyncBranchEvent(
                    repositoryId = repo.id,
                    branchName = req.defaultBranch,
                    newHeadCommit = headCommitHash,
                    lastCommitAtUtc = LocalDateTime.now()
                ),
                creator = repo.owner
            )
            ResponseEntity.ok().build()
        } catch (e: Exception) {
            log.error(e) { "[GitStorageInternal] init failed repoId=${req.repositoryId}" }
            ResponseEntity.internalServerError().build()
        }
    }
}

data class GitStorageInitRequest(
    val repositoryId: Long,
    val defaultBranch: String,
    val initializeReadme: Boolean,
    val gitignoreTemplate: String?,
    val licenseTemplate: String?
)
