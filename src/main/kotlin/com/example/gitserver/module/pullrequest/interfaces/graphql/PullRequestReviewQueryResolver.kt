package com.example.gitserver.module.pullrequest.interfaces.graphql

import com.example.gitserver.module.pullrequest.application.command.handler.PullRequestReviewCommandHandler
import com.example.gitserver.module.pullrequest.application.query.PullRequestReviewQueryService
import com.example.gitserver.module.pullrequest.interfaces.dto.MergeCheckResult
import com.example.gitserver.module.pullrequest.interfaces.dto.PullRequestReviewSummary
import com.example.gitserver.module.pullrequest.interfaces.dto.PullRequestReviewerNode
import com.example.gitserver.module.repository.interfaces.dto.RepositoryUserResponse
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller



@Controller
class PullRequestReviewQueryResolver(
    private val service: PullRequestReviewQueryService,
    private val reviewCmdHandler: PullRequestReviewCommandHandler
) {
    @QueryMapping
    fun repositoryPullRequestReviewers(@Argument prId: Long): List<PullRequestReviewerNode> =
        service.listReviewers(prId).map { v ->
            PullRequestReviewerNode(
                user = RepositoryUserResponse(v.userId, v.nickname, v.profileImageUrl),
                status = v.status,
                reviewedAt = v.reviewedAt
            )
        }

    @QueryMapping
    fun repositoryPullRequestReviewSummary(@Argument prId: Long): PullRequestReviewSummary {
        val s = service.summary(prId)
        return PullRequestReviewSummary(s.total, s.pending, s.approved, s.changesRequested, s.dismissed)
    }

    @QueryMapping
    fun repositoryPullRequestMergeCheck(@Argument prId: Long): MergeCheckResult {
        val ok = reviewCmdHandler.isMergeAllowed(prId)
        val reason = if (ok) null else "모든 리뷰어 승인 필요 또는 변경요청이 남아 있습니다."
        return MergeCheckResult(ok, reason)
    }
}
