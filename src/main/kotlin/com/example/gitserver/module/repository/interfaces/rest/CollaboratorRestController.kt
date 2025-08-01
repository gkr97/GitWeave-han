package com.example.gitserver.module.repository.interfaces.rest

import com.example.gitserver.common.response.ApiResponse
import com.example.gitserver.module.repository.application.command.handler.CollaboratorCommandHandler
import com.example.gitserver.module.repository.application.query.CollaboratorQueryService
import com.example.gitserver.module.user.application.service.RepoUserSearchService
import com.example.gitserver.module.repository.interfaces.dto.CollaboratorResponse
import com.example.gitserver.module.repository.interfaces.dto.UserSearchResponse
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/repositories")
class CollaboratorRestController(
    private val collaboratorCommandHandler: CollaboratorCommandHandler,
    private val userRepository: UserRepository,
    private val collaboratorQueryService: CollaboratorQueryService,
    private val repoUserSearchService: RepoUserSearchService,
) {

    @Operation(summary = "Collaborator 초대")
    @PostMapping("/{repoId}/collaborators")
    fun invite(
        @PathVariable repoId: Long,
        @RequestParam userId: Long,
        @AuthenticationPrincipal userDetails: UserDetails
    ): ResponseEntity<ApiResponse<String>> {
        val requester = userRepository.findByEmailAndIsDeletedFalse(userDetails.username)
            ?: throw IllegalArgumentException("사용자 없음")

        collaboratorCommandHandler.inviteCollaborator(repoId, userId, requester.id)
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "초대 요청이 전송되었습니다."))
    }

    @Operation(summary = "Collaborator 초대 수락")
    @PostMapping("/{repoId}/collaborators/accept")
    fun accept(
        @PathVariable repoId: Long,
        @AuthenticationPrincipal userDetails: UserDetails
    ): ResponseEntity<ApiResponse<String>> {
        val user = userRepository.findByEmailAndIsDeletedFalse(userDetails.username)
            ?: throw IllegalArgumentException("사용자 없음")

        collaboratorCommandHandler.acceptInvitation(repoId, user.id)
        return ResponseEntity.ok(
            ApiResponse.success(HttpStatus.OK.value(), "초대를 수락했습니다.")
        )
    }

    @Operation(summary = "Collaborator 초대 거절")
    @PostMapping("/{repoId}/collaborators/reject")
    fun reject(
        @PathVariable repoId: Long,
        @AuthenticationPrincipal userDetails: UserDetails
    ): ResponseEntity<ApiResponse<String>> {
        val user = userRepository.findByEmailAndIsDeletedFalse(userDetails.username)
            ?: throw IllegalArgumentException("사용자 없음")

        collaboratorCommandHandler.rejectInvitation(repoId, user.id)
        return ResponseEntity.ok(
            ApiResponse.success(HttpStatus.OK.value(), "초대를 거절했습니다.")
        )
    }

    @Operation(summary = "Collaborator 삭제 (소유자만 가능)")
    @DeleteMapping("/{repoId}/collaborators/{userId}")
    fun delete(
        @PathVariable repoId: Long,
        @PathVariable userId: Long,
        @AuthenticationPrincipal userDetails: UserDetails
    ): ResponseEntity<ApiResponse<String>> {
        val requester = userRepository.findByEmailAndIsDeletedFalse(userDetails.username)
            ?: throw IllegalArgumentException("사용자 없음")

        collaboratorCommandHandler.removeCollaborator(repoId, userId, requester.id)
        return ResponseEntity.ok(
            ApiResponse.success(HttpStatus.OK.value(), "Collaborator가 삭제되었습니다.")
        )
    }

    @Operation(summary = "Collaborator 목록 조회")
    @GetMapping("/{repoId}/collaborators")
    fun list(
        @PathVariable repoId: Long,
        @AuthenticationPrincipal userDetails: UserDetails
    ): ResponseEntity<ApiResponse<List<CollaboratorResponse>>> {
        val user = userRepository.findByEmailAndIsDeletedFalse(userDetails.username)
            ?: throw IllegalArgumentException("사용자 없음")

        val collaborators = collaboratorQueryService.getCollaborators(repoId, user.id)
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Collaborator 목록 조회 성공", collaborators))
    }

    // TODO 권한 변경

    @GetMapping("/{repoId}/collaborators/search")
    fun searchUserToInvite(
        @PathVariable repoId: Long,
        @RequestParam keyword: String,
        @AuthenticationPrincipal userDetails: UserDetails
    ): ResponseEntity<ApiResponse<List<UserSearchResponse>>> {
        val requester = userRepository.findByEmailAndIsDeletedFalse(userDetails.username)
            ?: throw IllegalArgumentException("사용자 없음")

        val candidates = repoUserSearchService.searchUsers(repoId, keyword)
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "유저 초대 목록 조회 성공", candidates))
    }
}
