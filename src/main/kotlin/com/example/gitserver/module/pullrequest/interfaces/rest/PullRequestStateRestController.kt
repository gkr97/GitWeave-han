package com.example.gitserver.module.pullrequest.interfaces.rest

import com.example.gitserver.common.response.ApiResponse
import com.example.gitserver.module.pullrequest.application.MergePullRequestCommand
import com.example.gitserver.module.pullrequest.application.command.ClosePullRequestCommand
import com.example.gitserver.module.pullrequest.application.command.ReopenPullRequestCommand
import com.example.gitserver.module.pullrequest.application.command.handler.PullRequestStateCommandHandler
import com.example.gitserver.module.pullrequest.interfaces.dto.MergeRequestBody
import com.example.gitserver.module.user.domain.CustomUserDetails
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/repositories/{repoId}/pull-requests/{prId}")
class PullRequestStateRestController(
    private val handler: PullRequestStateCommandHandler
) {

    @Operation(summary = "PR 닫기")
    @PostMapping("/close")
    fun close(
        @PathVariable repoId: Long,
        @PathVariable prId: Long,
        @AuthenticationPrincipal user: CustomUserDetails
    ): ResponseEntity<ApiResponse<String>> {
        handler.handle(ClosePullRequestCommand(repoId, prId, user.getUserId()))
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "closed"))
    }

    @Operation(summary = "PR 다시 열기")
    @PostMapping("/reopen")
    fun reopen(
        @PathVariable repoId: Long,
        @PathVariable prId: Long,
        @AuthenticationPrincipal user: CustomUserDetails
    ): ResponseEntity<ApiResponse<String>> {
        handler.handle(ReopenPullRequestCommand(repoId, prId, user.getUserId()))
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "reopened"))
    }

    @Operation(summary = "PR 머지")
    @PostMapping("/merge")
    fun merge(
        @PathVariable repoId: Long,
        @PathVariable prId: Long,
        @RequestBody body: MergeRequestBody,
        @AuthenticationPrincipal user: CustomUserDetails
    ): ResponseEntity<ApiResponse<String>> {
        handler.handle(
            MergePullRequestCommand(
                repositoryId = repoId,
                pullRequestId = prId,
                requesterId = user.getUserId(),
                mergeType = body.mergeType,
                message = body.message
            )
        )
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "merged"))
    }
}
