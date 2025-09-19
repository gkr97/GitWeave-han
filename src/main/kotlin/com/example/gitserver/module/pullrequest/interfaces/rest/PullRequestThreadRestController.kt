package com.example.gitserver.module.pullrequest.interfaces.rest

import com.example.gitserver.common.response.ApiResponse
import com.example.gitserver.module.pullrequest.application.service.PullRequestThreadService
import com.example.gitserver.module.pullrequest.interfaces.dto.ThreadResolveResponse
import com.example.gitserver.module.pullrequest.interfaces.dto.ToggleThreadResolveRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/pull-requests/threads")
class PullRequestThreadRestController(
    private val threadService: PullRequestThreadService
) {
    @PatchMapping("/{threadId}/resolve")
    fun toggleResolve(
        @PathVariable threadId: Long,
        @RequestBody req: ToggleThreadResolveRequest
    ): ResponseEntity<ApiResponse<ThreadResolveResponse>> {
        val result = threadService.toggleResolved(threadId, req.resolved)
        return ResponseEntity.ok(
            ApiResponse.success(HttpStatus.OK.value(), "스레드 상태 변경 완료", result)
        )
    }
}
