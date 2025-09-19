package com.example.gitserver.module.pullrequest.interfaces.graphql

import com.example.gitserver.module.pullrequest.application.query.PullRequestQueryService
import com.example.gitserver.module.pullrequest.application.query.model.PullRequestDetail
import com.example.gitserver.module.pullrequest.application.query.model.PullRequestListConnection
import com.example.gitserver.module.user.domain.CustomUserDetails
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import kotlin.math.ceil

@Controller
class PullRequestQueryResolver(
    private val prRepo: PullRequestQueryService,
) {
    companion object {
        private const val DEFAULT_PAGE = 0
        private const val DEFAULT_SIZE = 20
        private const val MAX_SIZE = 100
    }

    @QueryMapping
    fun repositoryPullRequests(
        @Argument repositoryId: Long,
        @Argument page: Int?,
        @Argument size: Int?,
        @Argument keyword: String?,
        @Argument status: String?,
        @Argument sort: String?,
        @Argument direction: String?,
        @AuthenticationPrincipal user: CustomUserDetails?
    ): PullRequestListConnection {
        val p = (page ?: DEFAULT_PAGE).coerceAtLeast(0)
        val s = (size ?: DEFAULT_SIZE).coerceIn(1, MAX_SIZE)

        val (list, total) = prRepo.getList(
            repositoryId = repositoryId,
            keyword = keyword,
            status = status,
            sort = sort,
            direction = direction,
            page = p,
            size = s
        )

        val pages = if (total <= 0) 0 else ceil(total / s.toDouble()).toInt()
        return PullRequestListConnection(
            content = list,
            page = p,
            size = s,
            totalElements = total,
            totalPages = pages,
            hasNext = (p + 1) < pages
        )
    }

    @QueryMapping
    fun repositoryPullRequest(
        @Argument repositoryId: Long,
        @Argument prId: Long,
        @AuthenticationPrincipal user: CustomUserDetails?
    ): PullRequestDetail? = prRepo.getDetail(repositoryId, prId)

}
