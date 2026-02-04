package com.example.gitserver.module.pullrequest.interfaces.graphql

import com.example.gitserver.common.pagination.KeysetPaging
import com.example.gitserver.common.pagination.PagingValidator
import com.example.gitserver.module.pullrequest.application.query.PullRequestQueryService
import com.example.gitserver.module.pullrequest.application.query.model.PullRequestListItemConnection
import com.example.gitserver.module.pullrequest.application.query.model.PullRequestDetail
import com.example.gitserver.module.repository.interfaces.dto.RepoDetailResponse
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

@Controller
class PullRequestQueryResolver(
    private val prRepo: PullRequestQueryService,
) {
    @SchemaMapping(typeName = "RepositoryResponse", field = "pullRequestsConnection")
    fun pullRequestsConnection(
        repo: RepoDetailResponse,
        @Argument first: Int?,
        @Argument after: String?,
        @Argument last: Int?,
        @Argument before: String?,
        @Argument keyword: String?,
        @Argument status: String?,
        @Argument sort: String?,
        @Argument direction: String?
    ): PullRequestListItemConnection {
        val paging = KeysetPaging(
            first = first,
            after = after,
            last = last,
            before = before
        )
        PagingValidator.validate(paging)

        return prRepo.getConnection(
            repositoryId = repo.id,
            keyword = keyword,
            status = status,
            sort = sort,
            direction = direction,
            paging = paging
        )
    }

    @SchemaMapping(typeName = "RepositoryResponse", field = "pullRequest")
    fun pullRequest(
        repo: RepoDetailResponse,
        @Argument prId: Long,
    ): PullRequestDetail? = prRepo.getDetail(repo.id, prId)
}
