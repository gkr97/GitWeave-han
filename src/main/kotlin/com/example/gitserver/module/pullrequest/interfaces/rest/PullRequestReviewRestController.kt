package com.example.gitserver.module.pullrequest.interfaces.rest

import com.example.gitserver.common.response.ApiResponse
import com.example.gitserver.module.pullrequest.application.RequestChangesCommand
import com.example.gitserver.module.pullrequest.application.command.*
import com.example.gitserver.module.pullrequest.application.command.handler.PullRequestReviewCommandHandler
import com.example.gitserver.module.pullrequest.interfaces.dto.*
import com.example.gitserver.module.user.domain.CustomUserDetails
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/repositories/{repoId}/pull-requests/{prId}/reviews")
class PullRequestReviewRestController(
    private val handler: PullRequestReviewCommandHandler,
    private val userRepository: UserRepository
) {
    private fun requesterId(user: CustomUserDetails) =
        userRepository.findByEmailAndIsDeletedFalse(user.username)?.id
            ?: error("User not found")

    @Operation(summary = "리뷰어 배정")
    @PostMapping("/assign")
    fun assignReviewer(
        @PathVariable repoId: Long,
        @PathVariable prId: Long,
        @Valid @RequestBody req: AssignReviewerRequest,
        @AuthenticationPrincipal user: CustomUserDetails
    ): ResponseEntity<ApiResponse<String>> {
        handler.handle(AssignReviewerCommand(repoId, prId, requesterId(user), req.reviewerId))
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "리뷰어가 배정되었습니다."))
    }

    @Operation(summary = "리뷰어 해제")
    @PostMapping("/remove")
    fun removeReviewer(
        @PathVariable repoId: Long,
        @PathVariable prId: Long,
        @Valid @RequestBody req: RemoveReviewerRequest,
        @AuthenticationPrincipal user: CustomUserDetails
    ): ResponseEntity<ApiResponse<String>> {
        handler.handle(RemoveReviewerCommand(repoId, prId, requesterId(user), req.reviewerId))
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "리뷰어가 해제되었습니다."))
    }

    @Operation(summary = "리뷰 승인")
    @PostMapping("/approve")
    fun approve(
        @PathVariable repoId: Long,
        @PathVariable prId: Long,
        @AuthenticationPrincipal user: CustomUserDetails
    ): ResponseEntity<ApiResponse<String>> {
        handler.handle(ApproveReviewCommand(repoId, prId, requesterId(user)))
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "승인 완료"))
    }

    @Operation(summary = "변경 요청")
    @PostMapping("/changes-requested")
    fun requestChanges(
        @PathVariable repoId: Long,
        @PathVariable prId: Long,
        @Valid @RequestBody req: RequestChangesRequest,
        @AuthenticationPrincipal user: CustomUserDetails
    ): ResponseEntity<ApiResponse<String>> {
        handler.handle(RequestChangesCommand(repoId, prId, requesterId(user), req.reason))
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "변경요청 등록"))
    }

    @Operation(summary = "리뷰 상태 해지")
    @PostMapping("/dismiss")
    fun dismiss(
        @PathVariable repoId: Long,
        @PathVariable prId: Long,
        @RequestParam(required = false) reviewerId: Long?,
        @AuthenticationPrincipal user: CustomUserDetails
    ): ResponseEntity<ApiResponse<String>> {
        handler.handle(DismissReviewCommand(repoId, prId, requesterId(user), reviewerId))
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "리뷰 상태가 해지되었습니다."))
    }
}
