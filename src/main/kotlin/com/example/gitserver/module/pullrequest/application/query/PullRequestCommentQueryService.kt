package com.example.gitserver.module.pullrequest.application.query


import com.example.gitserver.module.pullrequest.application.query.model.PullRequestCommentItem
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestCommentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.format.DateTimeFormatter

@Service
class PullRequestCommentQueryService(
    private val commentRepository: PullRequestCommentRepository
) {
    private val fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    /**
     * 특정 Pull Request에 대한 댓글 목록을 조회합니다.
     *
     * @param prId Pull Request ID
     * @return 댓글 목록
     */
    @Transactional(readOnly = true)
    fun list(prId: Long): List<PullRequestCommentItem> =
        commentRepository.findAllByPullRequestIdOrderByCreated(prId).map {
            PullRequestCommentItem(
                id = it.id,
                authorId = it.author.id,
                content = it.content,
                commentType = it.commentType,
                filePath = it.filePath,
                lineNumber = it.lineNumber,
                createdAt = it.createdAt.format(fmt)
            )
        }
}
