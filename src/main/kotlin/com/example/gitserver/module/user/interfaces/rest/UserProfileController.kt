package com.example.gitserver.module.user.interfaces.rest

import com.example.gitserver.common.response.ApiResponse
import com.example.gitserver.module.repository.interfaces.dto.UserSearchResponse
import com.example.gitserver.module.user.application.command.service.UserProfileCommandService
import com.example.gitserver.module.user.application.query.UserProfileSearchQueryService
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
    private val userProfileCommandService: UserProfileCommandService,
    private val userSearchQueryService: UserProfileSearchQueryService
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

    @Operation(summary = "Search users globally (by name/email)")
    @GetMapping("/search")
    fun searchUsers(
        @RequestParam keyword: String,
        @RequestParam(required = false, defaultValue = "20") limit: Int,
        @AuthenticationPrincipal userDetails: CustomUserDetails
    ): ApiResponse<List<UserSearchResponse>> {
        val meId = userDetails.getUserId()
        val list = userSearchQueryService.search(keyword, limit, excludeUserId = meId)
        return ApiResponse.success(200, "유저 검색 성공", list)
    }
}
