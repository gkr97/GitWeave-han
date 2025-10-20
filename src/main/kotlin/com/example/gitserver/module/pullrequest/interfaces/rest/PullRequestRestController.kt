package com.example.gitserver.module.pullrequest.interfaces.rest

import com.example.gitserver.common.response.ApiResponse
import com.example.gitserver.module.pullrequest.application.command.CreatePullRequestCommand
import com.example.gitserver.module.pullrequest.application.command.UpdatePullRequestCommand
import com.example.gitserver.module.pullrequest.application.command.handler.CreatePullRequestHandler
import com.example.gitserver.module.pullrequest.application.command.handler.UpdatePullRequestCommandHandler
import com.example.gitserver.module.pullrequest.interfaces.dto.CreatePullRequestRequest
import com.example.gitserver.module.pullrequest.interfaces.dto.UpdatePullRequestRequest
import com.example.gitserver.module.user.domain.CustomUserDetails
import com.example.gitserver.module.user.exception.UserNotFoundException
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/repositories/{repoId}/pull-requests")
class PullRequestRestController(
    private val pullRequestCommandHandler: CreatePullRequestHandler,
    private val updatePullRequestCommandHandler: UpdatePullRequestCommandHandler,
    private val userRepository: UserRepository,
) {

    @Operation(summary = "PR 생성")
    @PostMapping
    fun createPullRequest(
        @PathVariable repoId: Long,
        @Valid @RequestBody req: CreatePullRequestRequest,
        @AuthenticationPrincipal userDetails: CustomUserDetails
    ): ResponseEntity<ApiResponse<Long>> {
        val user = userRepository.findByEmailAndIsDeletedFalse(userDetails.username)
            ?: throw UserNotFoundException(userDetails.getUserId())

        val prId = pullRequestCommandHandler.handle(
            CreatePullRequestCommand(
                repositoryId = repoId,
                authorId = user.id,
                sourceBranch = req.sourceBranch,
                targetBranch = req.targetBranch,
                title = req.title,
                description = req.description
            )
        )
        return ResponseEntity.ok(
            ApiResponse.success(HttpStatus.OK.value(), "PR이 생성되었습니다.", prId)
        )
    }

    @Operation(summary = "PR 수정 (제목/설명)")
    @PatchMapping("/{prId}")
    fun updatePullRequest(
        @PathVariable repoId: Long,
        @PathVariable prId: Long,
        @RequestBody req: UpdatePullRequestRequest,
        @AuthenticationPrincipal userDetails: CustomUserDetails
    ): ResponseEntity<ApiResponse<String>> {
        val user = userRepository.findByEmailAndIsDeletedFalse(userDetails.username)
            ?: throw UserNotFoundException(userDetails.getUserId())

        updatePullRequestCommandHandler.handle(
            UpdatePullRequestCommand(
                repositoryId = repoId,
                pullRequestId = prId,
                requesterId = user.id,
                title = req.title,
                description = req.description
            )
        )
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "PR이 수정되었습니다."))
    }
}
