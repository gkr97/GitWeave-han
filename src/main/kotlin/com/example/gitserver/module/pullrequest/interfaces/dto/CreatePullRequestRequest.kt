package com.example.gitserver.module.pullrequest.interfaces.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreatePullRequestRequest(
    @field:NotBlank(message = "sourceBranch는 필수입니다.")
    @field:Size(max = 100, message = "sourceBranch는 최대 100자입니다.")
    val sourceBranch: String,

    @field:NotBlank(message = "targetBranch는 필수입니다.")
    @field:Size(max = 100, message = "targetBranch는 최대 100자입니다.")
    val targetBranch: String,

    @field:NotBlank(message = "title은 필수입니다.")
    @field:Size(max = 255, message = "title은 최대 255자입니다.")
    val title: String,

    @field:Size(max = 10_000, message = "description은 최대 10000자입니다.")
    val description: String? = null
)