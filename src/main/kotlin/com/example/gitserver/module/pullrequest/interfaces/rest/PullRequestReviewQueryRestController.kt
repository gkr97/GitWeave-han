package com.example.gitserver.module.pullrequest.interfaces.rest

import com.example.gitserver.common.response.ApiResponse
import com.example.gitserver.module.pullrequest.application.command.handler.PullRequestReviewCommandHandler
import com.example.gitserver.module.pullrequest.application.query.PullRequestReviewQueryService
import com.example.gitserver.module.pullrequest.interfaces.dto.MergeCheckResult
import com.example.gitserver.module.pullrequest.interfaces.dto.PullRequestReviewSummary
import com.example.gitserver.module.pullrequest.interfaces.dto.PullRequestReviewerNode
import com.example.gitserver.module.repository.interfaces.dto.RepositoryUserResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/repositories/{repoId}/pull-requests/{prId}")
class PullRequestReviewQueryRestController(
    private val service: PullRequestReviewQueryService,
    private val reviewCmdHandler: PullRequestReviewCommandHandler
) {
    @GetMapping("/reviewers")
    fun reviewers(@PathVariable prId: Long): ResponseEntity<ApiResponse<List<PullRequestReviewerNode>>> {
        val nodes = service.listReviewers(prId).map { v ->
            PullRequestReviewerNode(
                user = RepositoryUserResponse(v.userId, v.nickname, v.profileImageUrl),
                status = v.status,
                reviewedAt = v.reviewedAt
            )
        }
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), null, nodes))
    }

    @GetMapping("/review-summary")
    fun reviewSummary(@PathVariable prId: Long): ResponseEntity<ApiResponse<PullRequestReviewSummary>> {
        val s = service.summary(prId)
        val summary = PullRequestReviewSummary(s.total, s.pending, s.approved, s.changesRequested, s.dismissed)
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), null, summary))
    }

    @GetMapping("/merge-check")
    fun mergeCheck(@PathVariable prId: Long): ResponseEntity<ApiResponse<MergeCheckResult>> {
        val ok = reviewCmdHandler.isMergeAllowed(prId)
        val reason = if (ok) null else "모든 리뷰어 승인 필요 또는 변경요청이 남아 있습니다."
        val result = MergeCheckResult(ok, reason)
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), null, result))
    }
}
