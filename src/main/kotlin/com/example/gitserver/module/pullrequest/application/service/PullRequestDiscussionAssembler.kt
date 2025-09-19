package com.example.gitserver.module.pullrequest.application.service

import com.example.gitserver.module.pullrequest.application.query.support.ParsedHunk
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestCommentRepository
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestThreadRepository
import com.example.gitserver.module.pullrequest.interfaces.dto.InlineThreadSummary
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PullRequestDiscussionAssembler(
    private val commentRepo: PullRequestCommentRepository,
    private val threadRepo: PullRequestThreadRepository
) {

    /**
     * 주어진 Pull Request ID와 파일 경로에 대한 인라인 스레드 요약 목록을 생성합니다.
     *
     * @param prId Pull Request ID
     * @param filePath 파일 경로
     * @param parsed 파싱된 헝크 리스트
     * @return 인라인 스레드 요약 리스트
     */

    @Transactional(readOnly = true)
    fun buildThreads(
        prId: Long,
        filePath: String,
        parsed: List<ParsedHunk>
    ): List<InlineThreadSummary> {
        data class Key(val line: Int)
        val lineToAnchor = mutableMapOf<Key, String>()

        parsed.forEachIndexed { hIdx, h ->
            h.lines.forEach { l ->
                val anchor = "H${hIdx}-P${l.position}"
                l.newLine?.let { nl -> lineToAnchor[Key(nl)] = anchor }
            }
        }

        val comments = commentRepo
            .findAllByPullRequestIdAndFilePathOrderByCreatedAt(prId, filePath)
            .filter { it.commentType.equals("inline", true) && it.lineNumber != null }

        val grouped = comments.mapNotNull { c ->
            val anchor = lineToAnchor[Key(c.lineNumber!!)] ?: return@mapNotNull null
            anchor to c
        }.groupBy({ it.first }, { it.second })

        return grouped.map { (anchor, list) ->
            val threadId = list.firstNotNullOfOrNull { it.thread?.id }
                ?: list.minOf { it.id }

            val resolved = threadId.let { id ->
                id.let { threadRepo.findById(it).map { t -> t.resolved }.orElse(false) } ?: false
            }

            InlineThreadSummary(
                anchor = anchor,
                threadId = threadId,
                totalComments = list.size,
                resolved = resolved
            )
        }.sortedBy { it.threadId }
    }
}

