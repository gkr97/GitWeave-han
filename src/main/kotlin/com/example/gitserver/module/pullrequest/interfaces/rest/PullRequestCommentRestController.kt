package com.example.gitserver.module.pullrequest.interfaces.rest

import com.example.gitserver.common.response.ApiResponse
import com.example.gitserver.module.pullrequest.application.command.CreatePullRequestCommentCommand
import com.example.gitserver.module.pullrequest.application.command.DeletePullRequestCommentCommand
import com.example.gitserver.module.pullrequest.application.command.handler.PullRequestCommentCommandHandler
import com.example.gitserver.module.pullrequest.application.query.PullRequestCommentQueryService
import com.example.gitserver.module.pullrequest.application.query.model.PullRequestCommentItem
import com.example.gitserver.module.pullrequest.interfaces.dto.CreatePullRequestCommentRequest
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
@RequestMapping("/api/v1/repositories/{repoId}/pull-requests/{prId}/comments")
class PullRequestCommentRestController(
    private val handler: PullRequestCommentCommandHandler,
    private val query: PullRequestCommentQueryService,
    private val userRepository: UserRepository
) {

    @Operation(summary = "코멘트 등록")
    @PostMapping
    fun create(
        @PathVariable repoId: Long,
        @PathVariable prId: Long,
        @Valid @RequestBody req: CreatePullRequestCommentRequest,
        @AuthenticationPrincipal user: CustomUserDetails
    ): ResponseEntity<ApiResponse<Long>> {
        val me = userRepository.findByEmailAndIsDeletedFalse(user.username)
            ?: throw UserNotFoundException(user.getUserId())

        val id = handler.handle(
            CreatePullRequestCommentCommand(
                repositoryId = repoId,
                pullRequestId = prId,
                authorId = me.id,
                content = req.content,
                commentType = req.commentType,
                filePath = req.filePath,
                lineNumber = req.lineNumber
            )
        )
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "코멘트가 등록되었습니다.", id))
    }

    @Operation(summary = "코멘트 목록 조회")
    @GetMapping
    fun list(
        @PathVariable prId: Long
    ): ResponseEntity<ApiResponse<List<PullRequestCommentItem>>> {
        val items = query.list(prId)
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "ok", items))
    }

    @Operation(summary = "코멘트 삭제")
    @DeleteMapping("/{commentId}")
    fun delete(
        @PathVariable repoId: Long,
        @PathVariable prId: Long,
        @PathVariable commentId: Long,
        @AuthenticationPrincipal user: CustomUserDetails
    ): ResponseEntity<ApiResponse<String>> {
        val me = userRepository.findByEmailAndIsDeletedFalse(user.username)
            ?: throw IllegalArgumentException("User not found")

        handler.handle(
            DeletePullRequestCommentCommand(
                repositoryId = repoId,
                pullRequestId = prId,
                commentId = commentId,
                requesterId = me.id
            )
        )
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "코멘트가 삭제되었습니다."))
    }
}
