package com.example.gitserver.module.pullrequest.interfaces.graphql

import com.example.gitserver.module.pullrequest.application.command.handler.PullRequestReviewCommandHandler
import com.example.gitserver.module.pullrequest.application.query.PullRequestReviewQueryService
import com.example.gitserver.module.pullrequest.interfaces.dto.MergeCheckResult
import com.example.gitserver.module.pullrequest.interfaces.dto.PullRequestReviewSummary
import com.example.gitserver.module.pullrequest.interfaces.dto.PullRequestReviewerNode
import com.example.gitserver.module.pullrequest.application.query.model.PullRequestDetail
import com.example.gitserver.module.repository.interfaces.dto.RepositoryUserResponse
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller



@Controller
class PullRequestReviewQueryResolver(
    private val service: PullRequestReviewQueryService,
    private val reviewCmdHandler: PullRequestReviewCommandHandler
) {
    @SchemaMapping(typeName = "PullRequestDetail", field = "reviewers")
    fun reviewers(pr: PullRequestDetail): List<PullRequestReviewerNode> =
        service.listReviewers(pr.id).map { v ->
            PullRequestReviewerNode(
                user = RepositoryUserResponse(v.userId, v.nickname, v.profileImageUrl),
                status = v.status,
                reviewedAt = v.reviewedAt
            )
        }

    @SchemaMapping(typeName = "PullRequestDetail", field = "reviewSummary")
    fun reviewSummary(pr: PullRequestDetail): PullRequestReviewSummary {
        val s = service.summary(pr.id)
        return PullRequestReviewSummary(s.total, s.pending, s.approved, s.changesRequested, s.dismissed)
    }

    @SchemaMapping(typeName = "PullRequestDetail", field = "mergeCheck")
    fun mergeCheck(pr: PullRequestDetail): MergeCheckResult {
        val ok = reviewCmdHandler.isMergeAllowed(pr.id)
        val reason = if (ok) null else "모든 리뷰어 승인 필요 또는 변경요청이 남아 있습니다."
        return MergeCheckResult(ok, reason)
    }
}
