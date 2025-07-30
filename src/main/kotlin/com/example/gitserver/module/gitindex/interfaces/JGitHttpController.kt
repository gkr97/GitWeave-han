package com.example.gitserver.module.gitindex.interfaces

import com.example.gitserver.module.gitindex.domain.event.GitEvent
import com.example.gitserver.module.gitindex.domain.service.impl.JGitRepoService
import com.example.gitserver.module.repository.application.service.GitRepositorySyncService
import com.example.gitserver.module.repository.application.service.RepositoryAccessService
import com.example.gitserver.module.repository.domain.event.GitEventPublisher
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import io.swagger.v3.oas.annotations.Operation
import org.eclipse.jgit.transport.PostReceiveHook
import org.eclipse.jgit.lib.Repository
import org.springframework.web.bind.annotation.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

@RestController
class JGitHttpController(
    private val repoAccessService: RepositoryAccessService,
    private val jGitRepoService: JGitRepoService,
    private val gitEventPublisher: GitEventPublisher,
    private val repositoryRepository: RepositoryRepository,
    private val gitRepositorySyncService: GitRepositorySyncService,
    private val userRepository: UserRepository
) {

    /**
     * username → userId 변환 유틸
     */
    private fun resolveUserId(username: String): Long {
        return userRepository.findByName(username)?.id
            ?: throw IllegalArgumentException("존재하지 않는 사용자: $username")
    }

    private fun openAuthorizedRepository(
        username: String,
        repo: String,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): Repository? {
        val userId = resolveUserId(username)
        val authHeader = request.getHeader("Authorization")
        val access = repoAccessService.checkAccess(repo, userId, authHeader)
        if (access !is RepositoryAccessService.AccessResult.Authorized) {
            unauthorizedResponse(response)
            return null
        }
        return try {
            jGitRepoService.openRepository(userId, repo)
        } catch (e: Exception) {
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
        jGitRepoService.advertiseRefs(service, repository, response)
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
        jGitRepoService.uploadPack(repository, request, response)
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
        val repository = openAuthorizedRepository(username, repo, request, response) ?: return

        val repoEntity = repositoryRepository.findByOwnerIdAndName(userId, repo)
            ?: throw IllegalArgumentException("Repository not found")
        val repositoryId = repoEntity.id

        val hook = PostReceiveHook { _, commands ->
            commands.forEach { cmd ->
                gitRepositorySyncService.syncBranch(
                    repositoryId = repositoryId,
                    branchName = cmd.refName.removePrefix("refs/heads/"),
                    newHeadCommit = if (cmd.newId.name != "0000000000000000000000000000000000000000")
                        cmd.newId.name else null
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
            }
        }
        jGitRepoService.receivePack(repository, request, response, hook)
    }

    private fun unauthorizedResponse(response: HttpServletResponse) {
        response.setHeader("WWW-Authenticate", "Basic realm=\"Git\"")
        response.sendError(401, "Unauthorized")
    }
}
