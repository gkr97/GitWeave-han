package com.example.gitserver.module.gitindex.interfaces

import com.example.gitserver.module.gitindex.domain.event.GitEvent
import com.example.gitserver.module.gitindex.infrastructure.git.GitProtocolAdapter
import com.example.gitserver.module.gitindex.infrastructure.redis.GitIndexEvictor
import com.example.gitserver.module.repository.application.command.handler.GitRepositorySyncHandler
import com.example.gitserver.module.repository.application.query.RepositoryAccessQueryService
import com.example.gitserver.module.repository.domain.event.GitEventPublisher
import com.example.gitserver.module.repository.domain.event.SyncBranchEvent
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import io.swagger.v3.oas.annotations.Operation
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.PostReceiveHook
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * JGit를 사용하여 Git HTTP 프로토콜을 처리하는 컨트롤러입니다.
 */
@RestController
class JGitHttpController(
    private val repoAccessService: RepositoryAccessQueryService,
    private val gitProtocolAdapter: GitProtocolAdapter,
    private val gitEventPublisher: GitEventPublisher,
    private val repositoryRepository: RepositoryRepository,
    private val gitRepositorySyncHandler: GitRepositorySyncHandler,
    private val userRepository: UserRepository,
    private val gitIndexEvictor: GitIndexEvictor
) {
    private fun resolveUserId(username: String): Long =
        userRepository.findByNameAndIsDeletedFalse(username)?.id
            ?: throw IllegalArgumentException("존재하지 않는 사용자: $username")

    private fun openAuthorizedRepository(
        username: String,
        repo: String,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): Repository? {
        val userId = resolveUserId(username)
        val authHeader = request.getHeader("Authorization")
        val access = repoAccessService.checkAccess(repo, userId, authHeader)
        if (access !is RepositoryAccessQueryService.AccessResult.Authorized) {
            unauthorizedResponse(response); return null
        }
        return try {
            gitProtocolAdapter.openRepository(userId, repo)
        } catch (_: Exception) {
            response.sendError(404, "Repository not found")
            null
        }
    }

    @Operation(summary = "Get repository info refs")
    @GetMapping("/{username}/{repo}.git/info/refs")
    fun getInfoRefs(
        @PathVariable username: String,
        @PathVariable repo: String,
        @RequestParam("service") service: String,
        request: HttpServletRequest,
        response: HttpServletResponse
    ) {
        val repository = openAuthorizedRepository(username, repo, request, response) ?: return
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
        val repository = openAuthorizedRepository(username, repo, request, response) ?: return
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
        val userId = resolveUserId(username)
        val userEntity = userRepository.findByIdAndIsDeletedFalse(userId)
            ?: throw IllegalArgumentException("존재하지 않는 사용자: $username")
        val repository = openAuthorizedRepository(username, repo, request, response) ?: return

        val repoEntity = repositoryRepository.findByOwnerIdAndNameAndIsDeletedFalse(userId, repo)
            ?: throw IllegalArgumentException("Repository not found")
        val repositoryId = repoEntity.id

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

                gitEventPublisher.publish(
                    GitEvent(
                        eventType = "PUSH",
                        repositoryId = repositoryId,
                        ownerId = userId,
                        name = repo,
                        branch = cmd.refName,
                        oldrev = cmd.oldId.name,
                        newrev = cmd.newId.name
                    )
                )
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
