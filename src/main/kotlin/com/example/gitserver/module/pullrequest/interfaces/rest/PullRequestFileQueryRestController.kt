package com.example.gitserver.module.pullrequest.interfaces.rest

import com.example.gitserver.common.response.ApiResponse
import com.example.gitserver.module.pullrequest.application.query.PullRequestDiffQueryService
import com.example.gitserver.module.pullrequest.application.query.PullRequestFileQueryService
import com.example.gitserver.module.pullrequest.application.query.model.PullRequestFileDiffItem
import com.example.gitserver.module.pullrequest.application.query.model.PullRequestFileItem
import com.example.gitserver.module.pullrequest.application.query.support.UnifiedDiffMapper
import com.example.gitserver.module.pullrequest.application.service.PullRequestDiscussionAssembler
import com.example.gitserver.module.pullrequest.interfaces.dto.FileDiffResponse
import com.example.gitserver.module.user.domain.CustomUserDetails
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/repositories/{repoId}/pull-requests/{prId}")
class PullRequestFileQueryRestController(
    private val fileService: PullRequestFileQueryService,
    private val diffService: PullRequestDiffQueryService,
    private val discussionAssembler: PullRequestDiscussionAssembler
) {
    @GetMapping("/files")
    fun listFiles(
        @PathVariable prId: Long,
        @AuthenticationPrincipal user: CustomUserDetails?
    ): ResponseEntity<ApiResponse<List<PullRequestFileItem>>> {
        val files = fileService.listFiles(prId, user?.getUserId())
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), null, files))
    }

    @GetMapping("/diffs")
    fun listDiffs(
        @PathVariable prId: Long,
        @AuthenticationPrincipal user: CustomUserDetails?
    ): ResponseEntity<ApiResponse<List<PullRequestFileDiffItem>>> {
        val diffs = fileService.listDiffs(prId, user?.getUserId())
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), null, diffs))
    }

    @GetMapping("/file-diff")
    fun getFileDiff(
        @PathVariable repoId: Long,
        @PathVariable prId: Long,
        @RequestParam filePath: String,
        @RequestParam(required = false) forceFull: Boolean?,
        @AuthenticationPrincipal user: CustomUserDetails?
    ): ResponseEntity<ApiResponse<FileDiffResponse>> {
        val safeFilePath = com.example.gitserver.common.util.PathSecurityUtils.sanitizePath(filePath)
        val userId = user?.getUserId()

        val files = fileService.listFiles(prId, userId)
        val fileMeta = files.firstOrNull { it.path == safeFilePath }
            ?: error("PR($prId)에서 파일 메타데이터를 찾을 수 없습니다. path=$filePath")

        if (fileMeta.isBinary) {
            val response = UnifiedDiffMapper.toResponse(
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
            return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), null, response))
        }

        val (parsed, truncated) = diffService.getFileParsedHunks(
            repositoryId = repoId,
            prId = prId,
            path = safeFilePath,
            totalFiles = files.size,
            forceFull = forceFull == true
        )

        val threads = if (!truncated) {
            discussionAssembler.buildThreads(prId, safeFilePath, parsed)
        } else emptyList()

        val response = UnifiedDiffMapper.toResponse(
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
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), null, response))
    }
}
