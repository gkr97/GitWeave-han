package com.example.gitserver.module.gitindex.storage.interfaces

import com.example.gitserver.module.gitindex.storage.infrastructure.git.GitProtocolAdapter
import com.example.gitserver.module.gitindex.indexer.infrastructure.redis.GitIndexEvictor
import com.example.gitserver.module.repository.application.command.handler.GitRepositorySyncHandler
import com.example.gitserver.module.repository.application.query.RepositoryAccessQueryService
import com.example.gitserver.module.repository.domain.event.RepositoryPushed
import com.example.gitserver.module.repository.domain.event.SyncBranchEvent
import com.example.gitserver.module.repository.exception.RepositoryNotFoundException
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.user.exception.UserNotFoundException
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import com.example.gitserver.common.util.LogContext
import com.example.gitserver.module.gitindex.storage.infrastructure.routing.GitRouteRedirector
import io.swagger.v3.oas.annotations.Operation
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import mu.KotlinLogging
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.PostReceiveHook
import org.springframework.context.ApplicationEventPublisher
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.springframework.context.annotation.Profile

/**
 * JGit를 사용하여 Git HTTP 프로토콜을 처리하는 컨트롤러입니다.
 */
@RestController
@Profile("gitstorage")
class JGitHttpController(
    private val repoAccessService: RepositoryAccessQueryService,
    private val gitProtocolAdapter: GitProtocolAdapter,
    private val events: ApplicationEventPublisher,
    private val repositoryRepository: RepositoryRepository,
    private val gitRepositorySyncHandler: GitRepositorySyncHandler,
    private val userRepository: UserRepository,
    private val gitIndexEvictor: GitIndexEvictor,
    private val gitRouteRedirector: GitRouteRedirector
) {
    private val log = KotlinLogging.logger {}
    private fun resolveUserId(username: String): Long =
        userRepository.findByNameAndIsDeletedFalse(username)?.id
            ?: throw UserNotFoundException(0L) // username으로 찾을 수 없음

    private fun authorizeUser(
        username: String,
        repo: String,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): Long? {
        val userId = resolveUserId(username)
        val authHeader = request.getHeader("Authorization")
        val access = repoAccessService.checkAccess(repo, userId, authHeader)
        if (access !is RepositoryAccessQueryService.AccessResult.Authorized) {
            unauthorizedResponse(response); return null
        }
        return userId
    }

    private fun openRepository(
        userId: Long,
        repo: String,
        response: HttpServletResponse
    ): Repository? {
        return try {
            gitProtocolAdapter.openRepository(userId, repo)
        } catch (_: Exception) {
            response.sendError(404, "Repository not found")
            null
        }
    }

    private fun resolveRepoId(userId: Long, repo: String): Long? =
        repositoryRepository.findByOwnerIdAndNameAndIsDeletedFalse(userId, repo)?.id

    @Operation(summary = "Get repository info refs")
    @GetMapping("/{username}/{repo}.git/info/refs")
    fun getInfoRefs(
        @PathVariable username: String,
        @PathVariable repo: String,
        @RequestParam("service") service: String,
        request: HttpServletRequest,
        response: HttpServletResponse
    ) {
        val userId = authorizeUser(username, repo, request, response) ?: return
        val repoId = resolveRepoId(userId, repo)
        if (repoId == null) {
            response.sendError(404, "Repository not found")
            return
        }
        if (gitRouteRedirector.maybeRedirectRead(repoId, request, response)) return

        val repository = openRepository(userId, repo, response) ?: return
        gitProtocolAdapter.advertiseRefs(service, repository, response)
    }

    @Operation(summary = "Get repository capabilities")
    @PostMapping(
        value = ["/{username}/{repo}.git/git-upload-pack"],
        consumes = ["application/x-git-upload-pack-request"],
        produces = ["application/x-git-upload-pack-result"]
    )
    fun uploadPack(
        @PathVariable username: String,
        @PathVariable repo: String,
        request: HttpServletRequest,
        response: HttpServletResponse
    ) {
        val userId = authorizeUser(username, repo, request, response) ?: return
        val repoId = resolveRepoId(userId, repo)
        if (repoId == null) {
            response.sendError(404, "Repository not found")
            return
        }
        if (gitRouteRedirector.maybeRedirectRead(repoId, request, response)) return

        val repository = openRepository(userId, repo, response) ?: return
        gitProtocolAdapter.uploadPack(repository, request, response)
    }

    @Operation(summary = "Receive pack for pushing changes")
    @PostMapping(
        value = ["/{username}/{repo}.git/git-receive-pack"],
        consumes = ["application/x-git-receive-pack-request"],
        produces = ["application/x-git-receive-pack-result"]
    )
    fun receivePack(
        @PathVariable username: String,
        @PathVariable repo: String,
        request: HttpServletRequest,
        response: HttpServletResponse
    ) {
        val userId = authorizeUser(username, repo, request, response) ?: return
        val userEntity = userRepository.findByIdAndIsDeletedFalse(userId)
            ?: throw UserNotFoundException(userId)

        val repoEntity = repositoryRepository.findByOwnerIdAndNameAndIsDeletedFalse(userId, repo)
            ?: throw RepositoryNotFoundException(0L) // repo name으로 찾을 수 없음
        val repositoryId = repoEntity.id

        if (gitRouteRedirector.maybeRedirectWrite(repositoryId, request, response)) return

        val repository = openRepository(userId, repo, response) ?: return

        val hook = PostReceiveHook { _, commands ->
            commands.forEach { cmd ->
                val zero = "0000000000000000000000000000000000000000"
                val newHash = cmd.newId.name
                val isDelete = (newHash == zero)

                val committedAt: LocalDateTime? = if (!isDelete) {
                    RevWalk(repository).use { rw ->
                        val commit = rw.parseCommit(cmd.newId)
                        LocalDateTime.ofInstant(
                            Instant.ofEpochSecond(commit.commitTime.toLong()),
                            ZoneId.systemDefault()
                        )
                    }
                } else null

                gitRepositorySyncHandler.handle(
                    SyncBranchEvent(
                        repositoryId = repositoryId,
                        branchName = cmd.refName,
                        newHeadCommit = if (!isDelete) newHash else null,
                        lastCommitAtUtc = committedAt
                    ),
                    creator = userEntity
                )

                LogContext.with(
                    "eventType" to "PUSH",
                    "repoId" to repositoryId.toString(),
                    "branch" to cmd.refName
                ) {
                    log.info { "[GitHttp] push event published" }
                    events.publishEvent(
                        RepositoryPushed(
                            repositoryId = repositoryId,
                            ownerId = userId,
                            name = repo,
                            branch = cmd.refName,
                            oldrev = cmd.oldId.name,
                            newrev = cmd.newId.name,
                            actorId = userId
                        )
                    )
                }
                gitIndexEvictor.evictAllOfRepository(repositoryId)
            }
        }
        gitProtocolAdapter.receivePack(repository, request, response, hook)
    }

    private fun unauthorizedResponse(response: HttpServletResponse) {
        response.setHeader("WWW-Authenticate", "Basic realm=\"Git\"")
        response.sendError(401, "Unauthorized")
    }
}
