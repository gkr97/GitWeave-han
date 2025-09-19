package com.example.gitserver.module.pullrequest.interfaces.graphql

import com.example.gitserver.common.pagination.KeysetPaging
import com.example.gitserver.common.pagination.PagingValidator
import com.example.gitserver.common.pagination.Connection
import com.example.gitserver.module.pullrequest.application.query.model.PullRequestDetail
import com.example.gitserver.module.pullrequest.application.service.PullRequestCommitConnectionService
import com.example.gitserver.module.repository.interfaces.dto.CommitResponse
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

@Controller
class PullRequestCommitConnectionResolver(
    private val svc: PullRequestCommitConnectionService
) {
    @SchemaMapping(typeName = "PullRequestDetail", field = "commitsConnection")
    fun commitsConnection(
        source: PullRequestDetail,
        @Argument first: Int?,
        @Argument after: String?,
        @Argument last: Int?,
        @Argument before: String?,
    ): Connection<CommitResponse> {
        val paging = KeysetPaging(first = first, after = after, last = last, before = before)
        PagingValidator.validate(paging)
        return svc.connection(
            prId = source.id,
            repoId = source.repositoryId,
            paging = paging
        )
    }
}
