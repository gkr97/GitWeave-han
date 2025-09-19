package com.example.gitserver.module.pullrequest.application.service

import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestThreadRepository
import com.example.gitserver.module.pullrequest.interfaces.dto.ThreadResolveResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PullRequestThreadService(
    private val threadRepo: PullRequestThreadRepository
) {

    /**
     * 스레드 토글 변경
     *
     * @param threadId 스레드 ID
     * @param resolved 해결 상태
     * @return 변경된 스레드 정보
     */
    @Transactional
    fun toggleResolved(threadId: Long, resolved: Boolean): ThreadResolveResponse {
        val thread = threadRepo.findById(threadId)
            .orElseThrow { IllegalArgumentException("Thread not found: $threadId") }

        thread.resolved = resolved
        threadRepo.save(thread)

        return ThreadResolveResponse(thread.id, thread.resolved)
    }
}