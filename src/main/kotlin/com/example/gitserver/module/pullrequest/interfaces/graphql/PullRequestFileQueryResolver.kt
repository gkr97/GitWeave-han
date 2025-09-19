package com.example.gitserver.module.pullrequest.interfaces.graphql

import com.example.gitserver.module.pullrequest.application.query.PullRequestFileQueryService
import com.example.gitserver.module.pullrequest.application.query.PullRequestDiffQueryService
import com.example.gitserver.module.pullrequest.application.service.PullRequestDiscussionAssembler
import com.example.gitserver.module.pullrequest.application.query.support.UnifiedDiffMapper
import com.example.gitserver.module.pullrequest.interfaces.dto.FileDiffResponse
import com.example.gitserver.module.user.domain.CustomUserDetails
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller

@Controller
class PullRequestFileQueryResolver(
    private val fileService: PullRequestFileQueryService,
    private val diffService: PullRequestDiffQueryService,
    private val discussionAssembler: PullRequestDiscussionAssembler
) {

    @QueryMapping
    fun repositoryPullRequestFiles(
        @Argument prId: Long,
        @AuthenticationPrincipal user: CustomUserDetails?
    ) = fileService.listFiles(prId, user?.getUserId())

    @QueryMapping
    fun repositoryPullRequestDiffs(
        @Argument prId: Long,
        @AuthenticationPrincipal user: CustomUserDetails?
    ) = fileService.listDiffs(prId, user?.getUserId())

    @QueryMapping
    fun repositoryPullRequestFileDiff(
        @Argument repositoryId: Long,
        @Argument prId: Long,
        @Argument filePath: String,
        @Argument forceFull: Boolean?,
        @AuthenticationPrincipal user: CustomUserDetails?
    ): FileDiffResponse {
        val userId = user?.getUserId()

        val files = fileService.listFiles(prId, userId)
        val fileMeta = files.firstOrNull { it.filePath == filePath }
            ?: error("PR($prId)에서 파일 메타데이터를 찾을 수 없습니다. path=$filePath")

        if (fileMeta.isBinary) {
            return UnifiedDiffMapper.toResponse(
                filePath = fileMeta.filePath,
                oldPath = fileMeta.oldPath,
                status = fileMeta.status,
                isBinary = true,
                headBlobHash = fileMeta.headBlobHash,
                baseBlobHash = fileMeta.baseBlobHash,
                additions = fileMeta.additions,
                deletions = fileMeta.deletions,
                parsedChunks = emptyList(),
                threads = emptyList(),
                truncated = false
            )
        }

        val (parsed, truncated) = diffService.getFileParsedHunks(
            repositoryId = repositoryId,
            prId = prId,
            path = filePath,
            totalFiles = files.size,
            forceFull = forceFull == true
        )

        val threads = if (!truncated) {
            discussionAssembler.buildThreads(prId, filePath, parsed)
        } else emptyList()

        return UnifiedDiffMapper.toResponse(
            filePath = fileMeta.filePath,
            oldPath = fileMeta.oldPath,
            status = fileMeta.status,
            isBinary = false,
            headBlobHash = fileMeta.headBlobHash,
            baseBlobHash = fileMeta.baseBlobHash,
            additions = fileMeta.additions,
            deletions = fileMeta.deletions,
            parsedChunks = parsed,
            threads = threads,
            truncated = truncated
        )
    }
}

