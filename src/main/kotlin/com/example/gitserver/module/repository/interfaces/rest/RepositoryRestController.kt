package com.example.gitserver.module.repository.interfaces.rest

import com.example.gitserver.common.jwt.GitPatAuthenticationFilter
import com.example.gitserver.common.response.ApiResponse
import com.example.gitserver.module.repository.application.command.CreateRepositoryCommand
import com.example.gitserver.module.repository.application.command.DeleteRepositoryCommand
import com.example.gitserver.module.repository.application.command.handler.CreateRepositoryCommandHandler
import com.example.gitserver.module.repository.application.command.handler.DeleteRepositoryCommandHandler
import com.example.gitserver.module.repository.application.query.RepositoryDownloadQueryService
import com.example.gitserver.module.repository.interfaces.dto.CreateRepositoryRequest
import com.example.gitserver.module.repository.interfaces.dto.RepositoryResponse
import com.example.gitserver.module.user.domain.CustomUserDetails
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody

@RestController
@RequestMapping("/api/v1/repositories")
class RepositoryRestController(
    private val createHandler: CreateRepositoryCommandHandler,
    private val deleteHandler: DeleteRepositoryCommandHandler,
    private val userRepository: UserRepository,
    private val repositoryDownloadQueryService: RepositoryDownloadQueryService,
    private val gitPatAuthenticationFilter: GitPatAuthenticationFilter
) {

    private val logger = mu.KotlinLogging.logger {}

    @Operation(summary = "Repository create")
    @PostMapping
    fun create(
        @Valid @RequestBody request: CreateRepositoryRequest,
        @AuthenticationPrincipal userDetails: UserDetails
    ): ResponseEntity<ApiResponse<RepositoryResponse>> {

        val user = userRepository.findByEmailAndIsDeletedFalse(userDetails.username)
            ?: throw IllegalArgumentException("인증된 사용자를 찾을 수 없습니다")

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

    @GetMapping("/{repoId}/download")
    fun downloadRepository(
        @PathVariable repoId: Long,
        @RequestParam(required = false, defaultValue = "main") branch: String,
        @AuthenticationPrincipal userDetails: CustomUserDetails
    ): ResponseEntity<StreamingResponseBody> {
        val downloadInfo = repositoryDownloadQueryService.prepareDownload(repoId, branch, userDetails.getUserId())
        val securityContext = SecurityContextHolder.getContext()
        val streamingBody = StreamingResponseBody { os ->
            val task = Runnable {
                val (input, process) = downloadInfo.streamSupplier()
                try {
                    input.use { it.copyTo(os); os.flush() }
                    val exitCode = process.waitFor()
                    if (exitCode != 0) {
                        logger.error { "git archive 실패(exitCode=$exitCode)" }
                        os.write("error".toByteArray())
                    }
                } catch (e: Exception) {
                    logger.error(e) { "다운로드 중 오류" }
                    os.write("error".toByteArray())
                } finally {
                    process.destroyForcibly()
                    os.close()
                }
            }
            DelegatingSecurityContextRunnable(task, securityContext).run()
        }
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=\"${downloadInfo.filename}\"")
            .header("Content-Type", "application/zip")
            .body(streamingBody)
    }





}
