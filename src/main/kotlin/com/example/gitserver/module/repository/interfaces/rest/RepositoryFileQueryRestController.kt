package com.example.gitserver.module.repository.interfaces.rest

import com.example.gitserver.common.response.ApiResponse
import com.example.gitserver.module.repository.application.query.RepositoryFileQueryService
import com.example.gitserver.module.repository.interfaces.dto.FileContentResponse
import com.example.gitserver.module.repository.interfaces.dto.TreeNodeResponse
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
@RequestMapping("/api/v1/repositories/{repoId}")
class RepositoryFileQueryRestController(
    private val repositoryFileQueryService: RepositoryFileQueryService
) {
    @GetMapping("/tree")
    fun getFileTree(
        @PathVariable repoId: Long,
        @RequestParam(required = false) path: String?,
        @RequestParam(required = false) branch: String?,
        @RequestParam(required = false) commitHash: String?,
        @AuthenticationPrincipal user: CustomUserDetails?
    ): ResponseEntity<ApiResponse<List<TreeNodeResponse>>> {
        val tree = repositoryFileQueryService.getFileTree(
            repositoryId = repoId,
            commitHash = commitHash,
            path = path,
            branch = branch,
            userId = user?.getUserId()
        )
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), null, tree))
    }

    @GetMapping("/blob")
    fun getFileContent(
        @PathVariable repoId: Long,
        @RequestParam path: String,
        @RequestParam(required = false) branch: String?,
        @RequestParam(required = false) commitHash: String?,
        @AuthenticationPrincipal user: CustomUserDetails?
    ): ResponseEntity<ApiResponse<FileContentResponse>> {
        val content = repositoryFileQueryService.getFileContent(
            repositoryId = repoId,
            commitHash = commitHash,
            path = path,
            branch = branch,
            userId = user?.getUserId()
        )
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), null, content))
    }
}
