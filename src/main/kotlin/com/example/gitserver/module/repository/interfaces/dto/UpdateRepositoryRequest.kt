package com.example.gitserver.module.repository.interfaces.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import jakarta.validation.constraints.Pattern

data class UpdateRepositoryRequest(
    @field:NotBlank(message = "저장소 이름은 필수 입니다.")
    @field:Size(min = 2, max = 50, message = "저장소 이름은 2~50자 이내여야 합니다.")
    @field:Pattern(
        regexp = "^[a-zA-Z0-9._-]+$",
        message = "저장소 이름은 영문, 숫자, ., _, -만 사용할 수 있습니다."
    )
    val name: String,

    @field:Size(max = 500, message = "설명은 최대 500자까지 허용 합니다.")
    val description: String? = null
)
