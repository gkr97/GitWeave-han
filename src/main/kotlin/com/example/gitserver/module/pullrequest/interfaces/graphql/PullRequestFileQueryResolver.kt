package com.example.gitserver.module.pullrequest.interfaces.graphql

import com.example.gitserver.module.pullrequest.application.query.PullRequestFileQueryService
import com.example.gitserver.module.pullrequest.application.query.PullRequestDiffQueryService
import com.example.gitserver.module.pullrequest.application.service.PullRequestDiscussionAssembler
import com.example.gitserver.module.pullrequest.application.query.support.UnifiedDiffMapper
import com.example.gitserver.module.pullrequest.interfaces.dto.FileDiffResponse
import com.example.gitserver.module.pullrequest.application.query.model.PullRequestDetail
import com.example.gitserver.module.user.domain.CustomUserDetails
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.ContextValue
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

@Controller
class PullRequestFileQueryResolver(
    private val fileService: PullRequestFileQueryService,
    private val diffService: PullRequestDiffQueryService,
    private val discussionAssembler: PullRequestDiscussionAssembler
) {

    @SchemaMapping(typeName = "PullRequestDetail", field = "files")
    fun files(
        pr: PullRequestDetail,
        @ContextValue(name = "currentUser", required = false) user: CustomUserDetails?
    ) = fileService.listFiles(pr.id, user?.getUserId())

    @SchemaMapping(typeName = "PullRequestDetail", field = "diffs")
    fun diffs(
        pr: PullRequestDetail,
        @ContextValue(name = "currentUser", required = false) user: CustomUserDetails?
    ) = fileService.listDiffs(pr.id, user?.getUserId())

    @SchemaMapping(typeName = "PullRequestDetail", field = "fileDiff")
    fun fileDiff(
        pr: PullRequestDetail,
        @Argument filePath: String,
        @Argument forceFull: Boolean?,
        @ContextValue(name = "currentUser", required = false) user: CustomUserDetails?
    ): FileDiffResponse {
        // Path Traversal 방어
        val safeFilePath = com.example.gitserver.common.util.PathSecurityUtils.sanitizePath(filePath)
        val currentUserId = user?.getUserId()

        val files = fileService.listFiles(pr.id, currentUserId)
        val fileMeta = files.firstOrNull { it.path == safeFilePath }
            ?: error("PR(${pr.id})에서 파일 메타데이터를 찾을 수 없습니다. path=$filePath")

        if (fileMeta.isBinary) {
            return UnifiedDiffMapper.toResponse(
                filePath = fileMeta.path,
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
            repositoryId = pr.repositoryId,
            prId = pr.id,
            path = safeFilePath,
            totalFiles = files.size,
            forceFull = forceFull == true
        )

        val threads = if (!truncated) {
            discussionAssembler.buildThreads(pr.id, safeFilePath, parsed)
        } else emptyList()

        return UnifiedDiffMapper.toResponse(
            filePath = fileMeta.path,
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
