package com.example.gitserver.module.pullrequest.interfaces.graphql

import com.example.gitserver.module.gitindex.application.query.CommitQueryService
import com.example.gitserver.module.pullrequest.application.query.model.PullRequestDetail
import com.example.gitserver.module.pullrequest.application.service.PullRequestCommitMappingService
import com.example.gitserver.module.repository.interfaces.dto.CommitResponse
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

@Controller
class PullRequestDetailFieldResolver(
    private val mappingService: PullRequestCommitMappingService,
    private val commitQuery: CommitQueryService
) {

    companion object {
        const val MAX_COMMITS = 300
    }

    @SchemaMapping(typeName = "PullRequestDetail", field = "commits")
    fun commits(source: PullRequestDetail): List<CommitResponse> {
        val hashes = mappingService.listHashes(source.id).take(MAX_COMMITS)
        return hashes.mapNotNull { h -> commitQuery.getCommitInfo(source.repositoryId, h) }
    }
}
