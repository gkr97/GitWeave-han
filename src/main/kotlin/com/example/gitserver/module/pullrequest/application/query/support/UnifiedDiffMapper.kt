package com.example.gitserver.module.pullrequest.application.query.support

import com.example.gitserver.module.pullrequest.application.query.model.DiffChunk
import com.example.gitserver.module.pullrequest.application.query.model.DiffLine
import com.example.gitserver.module.pullrequest.application.query.model.DiffLineType
import com.example.gitserver.module.pullrequest.interfaces.dto.FileDiffResponse
import com.example.gitserver.module.pullrequest.interfaces.dto.InlineThreadSummary

object UnifiedDiffMapper {

    /**
    * diff 결과를 FileDiffResponse로 매핑
     */
    fun toResponse(
        filePath: String,
        oldPath: String?,
        status: String,
        isBinary: Boolean,
        headBlobHash: String?,
        baseBlobHash: String?,
        additions: Int,
        deletions: Int,
        parsedChunks: List<ParsedHunk>,
        threads: List<InlineThreadSummary>,
        truncated: Boolean = false,
    ): FileDiffResponse {
        val commentCountByAnchor: Map<String, Int> =
            threads.groupBy { it.anchor }.mapValues { (_, v) -> v.sumOf { it.totalComments } }

        val chunks = parsedChunks.mapIndexed { idx, h ->
            DiffChunk(
                hunkIndex = idx,
                header = h.header,
                oldStart = h.oldStart,
                newStart = h.newStart,
                lines = h.lines.map { l ->
                    val anchor = "H${idx}-P${l.position}"
                    DiffLine(
                        type = when (l.type) {
                            '+' -> DiffLineType.ADDED
                            '-' -> DiffLineType.REMOVED
                            else -> DiffLineType.CONTEXT
                        },
                        oldLine = l.oldLine,
                        newLine = l.newLine,
                        content = l.content,
                        position = l.position,
                        anchor = anchor,
                        commentCount = commentCountByAnchor[anchor] ?: (l.commentCount ?: 0)
                    )
                }
            )
        }

        return FileDiffResponse(
            filePath = filePath,
            oldPath = oldPath,
            status = status,
            isBinary = isBinary,
            headBlobHash = headBlobHash,
            baseBlobHash = baseBlobHash,
            additions = additions,
            deletions = deletions,
            chunks = chunks,
            threads = threads,
            truncated = truncated
        )
    }
}
