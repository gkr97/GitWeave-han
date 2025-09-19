package com.example.gitserver.module.repository.interfaces.rest

import com.example.gitserver.common.response.ApiResponse
import com.example.gitserver.module.repository.application.command.ChangeRepositoryVisibilityCommand
import com.example.gitserver.module.repository.application.command.CreateRepositoryCommand
import com.example.gitserver.module.repository.application.command.DeleteRepositoryCommand
import com.example.gitserver.module.repository.application.command.UpdateRepositoryCommand
import com.example.gitserver.module.repository.application.command.handler.ChangeRepoVisibilityCommandHandler
import com.example.gitserver.module.repository.application.command.handler.CreateRepositoryCommandHandler
import com.example.gitserver.module.repository.application.command.handler.DeleteRepositoryCommandHandler
import com.example.gitserver.module.repository.application.command.handler.UpdateRepositoryCommandHandler
import com.example.gitserver.module.repository.application.query.RepositoryDownloadQueryService
import com.example.gitserver.module.repository.interfaces.dto.ChangeVisibilityRequest
import com.example.gitserver.module.repository.interfaces.dto.CreateRepositoryRequest
import com.example.gitserver.module.repository.interfaces.dto.RepositoryResponse
import com.example.gitserver.module.repository.interfaces.dto.UpdateRepositoryRequest
import com.example.gitserver.module.user.domain.CustomUserDetails
import com.example.gitserver.module.user.exception.UserNotFoundException
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody

@RestController
@RequestMapping("/api/v1/repositories")
class RepositoryRestController(
    private val createHandler: CreateRepositoryCommandHandler,
    private val deleteHandler: DeleteRepositoryCommandHandler,
    private val updateRepositoryCommandHandler: UpdateRepositoryCommandHandler,
    private val changeRepoVisibilityCommandHandler: ChangeRepoVisibilityCommandHandler,
    private val userRepository: UserRepository,
    private val repositoryDownloadQueryService: RepositoryDownloadQueryService,
) {

    private val logger = mu.KotlinLogging.logger {}

    @Operation(summary = "Repository create")
    @PostMapping
    fun create(
        @Valid @RequestBody request: CreateRepositoryRequest,
        @AuthenticationPrincipal userDetails: CustomUserDetails
    ): ResponseEntity<ApiResponse<RepositoryResponse>> {

        val user = userRepository.findByEmailAndIsDeletedFalse(userDetails.username)
            ?: throw UserNotFoundException(userDetails.getUserId())

        val command = CreateRepositoryCommand(
            owner = user,
            name = request.name,
            description = request.description,
            visibilityCode = request.visibilityCode?: "private",
            defaultBranch = request.defaultBranch ?: "main",
            license = request.license,
            language = request.language,
            homepageUrl = request.homepageUrl,
            topics = request.topics,

            initializeReadme = request.initializeReadme,
            gitignoreTemplate = request.gitignoreTemplate,
            licenseTemplate = request.licenseTemplate,
            invitedUserIds = request.invitedUserIds ?: emptyList()
        )

        val repo = createHandler.handle(command)
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "저장소가 성공적으로 생성되었습니다.", RepositoryResponse.from(repo)))
    }

    @Operation(summary = "Repository soft delete")
    @DeleteMapping("/{repoId}")
    fun delete(
        @PathVariable repoId: Long,
        @AuthenticationPrincipal userDetails: CustomUserDetails,
    ): ResponseEntity<ApiResponse<String>> {
        deleteHandler.handle(
            DeleteRepositoryCommand(
                repositoryId = repoId,
                requesterEmail = userDetails.username
            )
        )
        return ResponseEntity.ok(
            ApiResponse.success(HttpStatus.OK.value(), "저장소가 삭제되었습니다.")
        )
    }

    @Operation(summary = "Repository download")
    @GetMapping("/{repoId}/download", produces = ["application/zip"])
    fun downloadRepository(
        @PathVariable repoId: Long,
        @RequestParam(required = false, defaultValue = "main") branch: String,
        @AuthenticationPrincipal userDetails: CustomUserDetails?
    ): ResponseEntity<StreamingResponseBody> {
        val userId = userDetails?.getUserId()
        val downloadInfo = repositoryDownloadQueryService.prepareDownload(repoId, branch, userId)

        val streamingBody = StreamingResponseBody { os ->
            val ins = downloadInfo.streamSupplier.invoke()
            ins.use {
                it.copyTo(os)
                os.flush()
            }
        }

        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=\"${downloadInfo.filename}\"")
            .header("Content-Type", "application/zip")
            .body(streamingBody)
    }


    @Operation(summary = "Repository update")
    @PatchMapping("/{repoId}")
    fun update(
        @PathVariable repoId: Long,
        @Valid @RequestBody request: UpdateRepositoryRequest,
        @AuthenticationPrincipal userDetails: CustomUserDetails
    ): ResponseEntity<ApiResponse<String>> {
        val command = UpdateRepositoryCommand(
            repositoryId = repoId,
            requesterId = userDetails.getUserId(),
            newName = request.name,
            newDescription = request.description
        )

        updateRepositoryCommandHandler.handle(command)
        return ResponseEntity.ok(
            ApiResponse.success(HttpStatus.OK.value(), "레포지터리 정보가 수정되었습니다.")
        )
    }

    @Operation(summary = "Change repository visibility")
    @PatchMapping("/{repoId}/visibility")
    fun changeVisibility(
        @PathVariable repoId: Long,
        @Valid @RequestBody request: ChangeVisibilityRequest,
        @AuthenticationPrincipal userDetails: CustomUserDetails
    ): ResponseEntity<ApiResponse<String>> {
        val command = ChangeRepositoryVisibilityCommand(
            repositoryId = repoId,
            requesterId = userDetails.getUserId(),
            newVisibility = request.visibility
        )
        changeRepoVisibilityCommandHandler.handle(command)
        return ResponseEntity.ok(
            ApiResponse.success(HttpStatus.OK.value(), "가시성이 변경되었습니다.")
        )
    }



}
