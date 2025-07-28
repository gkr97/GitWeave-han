package com.example.gitserver.module.user.interfaces.rest

import com.example.gitserver.module.user.application.service.PatService
import org.springframework.web.bind.annotation.*
import java.util.*

import org.springframework.security.core.annotation.AuthenticationPrincipal
import com.example.gitserver.module.user.domain.CustomUserDetails
import io.swagger.v3.oas.annotations.Operation

@RestController
@RequestMapping("/api/v1/pat")
class PatController(
    private val patService: PatService
) {

    /**
     * Git Personal Access Token (PAT)을 발급합니다.
     *
     * @param user 인증된 사용자 정보
     * @param description PAT 설명 (선택적)
     * @return 발급된 PAT
     */
    @Operation(summary = "Git Personal Access Token (PAT) 발급")
    @PostMapping("/issue")
    fun issuePat(
        @AuthenticationPrincipal user: CustomUserDetails,
        @RequestParam description: String?
    ): Map<String, String> {
        val rawToken = UUID.randomUUID().toString().replace("-", "")
        patService.issuePat(user.getUserId(), rawToken, description)
        return mapOf("pat" to rawToken)
    }

    /**
     * Git Personal Access Token (PAT)을 조회합니다.
     *
     * @param user 인증된 사용자 정보
     * @return 활성화된 PAT 목록
     */
    @Operation(summary = "Git Personal Access Token (PAT) 조회")
    @PostMapping("/revoke/{patId}")
    fun revokePat(
        @AuthenticationPrincipal user: CustomUserDetails,
        @PathVariable patId: Long
    ) {
        patService.deactivatePat(patId, user.getUserId())
    }
}
