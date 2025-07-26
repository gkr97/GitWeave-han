package com.example.gitserver.module.repository.interfaces.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateRepositoryRequest(
    @field:NotBlank(message = "저장소 이름은 필수입니다.")
    @field:Size(max = 50, message = "저장소 이름은 50자 이하만 허용됩니다.")
    val name: String,

    @field:Size(max = 255, message = "설명은 최대 255자까지 입력 가능합니다.")
    val description: String? = "",

    @field:NotBlank(message = "공개 여부는 필수입니다.")
    val visibilityCode: String? = "PUBLIC",

    @field:NotBlank(message = "기본 브랜치는 필수입니다.")
    val defaultBranch: String? = "main",

    @field:Size(max = 50)
    val license: String? = "",

    @field:Size(max = 50)
    val language: String? = "",

    @field:Size(max = 255)
    val homepageUrl: String? = "",

    @field:Size(max = 255)
    val topics: String? = "",

    val initializeReadme: Boolean = false,
    val gitignoreTemplate: String? = null,
    val licenseTemplate: String? = null,

    val invitedUserIds: List<Long>? = null
)
