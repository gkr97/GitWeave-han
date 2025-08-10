package com.example.gitserver.module.repository.interfaces.rest

import com.example.gitserver.common.response.ApiResponse
import com.example.gitserver.module.repository.application.command.service.BranchService
import com.example.gitserver.module.repository.interfaces.dto.CreateBranchRequest
import com.example.gitserver.module.user.domain.CustomUserDetails
import com.example.gitserver.module.user.exception.UserNotFoundException
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/repositories")
class BranchRestController(
    private val branchService: BranchService,
    private val userRepository: UserRepository
) {

    @Operation(summary = "delete branch")
    @DeleteMapping("/{repoId}/branches/{branchName}")
    fun deleteBranch(
        @PathVariable repoId: Long,
        @PathVariable branchName: String,
        @AuthenticationPrincipal userDetails: CustomUserDetails
    ): ResponseEntity<ApiResponse<String>> {
        val user = userRepository.findByEmailAndIsDeletedFalse(userDetails.username)
            ?: throw UserNotFoundException(userDetails.getUserId())
        branchService.deleteBranch(repoId, branchName, user.id)
        return ResponseEntity.ok(
            ApiResponse.success(HttpStatus.OK.value(), "브랜치가 삭제되었습니다.")
        )
    }

    @Operation(summary = "create branch")
    @PostMapping("/{repoId}/branches")
    fun createBranch(
        @PathVariable repoId: Long,
        @Valid @RequestBody request: CreateBranchRequest,
        @AuthenticationPrincipal userDetails: CustomUserDetails
    ): ResponseEntity<ApiResponse<Long>> {
        val user = userRepository.findByEmailAndIsDeletedFalse(userDetails.username)
            ?: throw UserNotFoundException(userDetails.getUserId())
        val branchId = branchService.createBranch(
            repositoryId = repoId,
            branchName = request.branchName,
            sourceBranch = request.sourceBranch,
            requesterId = user.id
        )
        return ResponseEntity.ok(
            ApiResponse.success(HttpStatus.OK.value(), "브랜치가 생성되었습니다.", branchId)
        )
    }



}
