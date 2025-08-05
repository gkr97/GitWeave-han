package com.example.gitserver.module.user.interfaces.rest

import com.example.gitserver.common.response.ApiResponse
import com.example.gitserver.module.user.application.command.service.UserProfileCommandService
import com.example.gitserver.module.user.domain.CustomUserDetails
import com.example.gitserver.module.user.interfaces.dto.UpdateNameRequest
import com.example.gitserver.module.user.interfaces.dto.UserProfileResponse
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/v1/users")
class UserProfileController(
    private val userProfileCommandService: UserProfileCommandService
) {

    /**
     * 내 프로필 조회
     */
    @Operation(summary = "my profile")
    @GetMapping("/me")
    fun getMyProfile(
        @AuthenticationPrincipal userDetails: CustomUserDetails
    ): ApiResponse<UserProfileResponse> {
        val user = userDetails.getUser()
        return ApiResponse.success(200, "내 프로필 조회", UserProfileResponse.from(user))
    }

    /**
     * 내 프로필 정보 수정
     */
    @Operation(summary = "Update my profile")
    @PatchMapping("/me/name")
    fun updateMyName(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        @RequestBody @Valid request: UpdateNameRequest
    ): ApiResponse<String> {
        val userId = userDetails.getUserId()
        userProfileCommandService.updateName(userId, request.name)
        return ApiResponse.success(200, "이름 변경 완료", request.name)
    }

    /**
     * 내 프로필 이미지 업로드
     */
    @Operation (summary = "Upload my profile image")
    @PostMapping("/me/image", consumes = ["multipart/form-data"])
    fun uploadMyProfileImage(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        @RequestPart("file") file: MultipartFile
    ): ApiResponse<String> {
        val userId = userDetails.getUserId()
        val url = userProfileCommandService.updateProfileImage(userId, file)
        return ApiResponse.success(200, "프로필 이미지 업로드 완료", url)
    }
}
