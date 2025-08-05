package com.example.gitserver.module.repository.interfaces.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class CreateBranchRequest(
    @field:NotBlank(message = "브랜치명은 필수입니다.")
    @field:Size(max = 50, message = "브랜치명은 최대 50자까지 허용합니다.")
    @field:Pattern(
        regexp = "^[a-zA-Z0-9._/-]+$",
        message = "브랜치명에는 영문, 숫자, ., _, /, -만 사용할 수 있습니다."
    )
    val branchName: String,

    @field:Size(max = 50, message = "소스 브랜치명은 최대 50자까지 허용합니다.")
    @field:Pattern(
        regexp = "^[a-zA-Z0-9._/-]+$",
        message = "소스 브랜치명에는 영문, 숫자, ., _, /, -만 사용할 수 있습니다."
    )
    val sourceBranch: String? = null
)
