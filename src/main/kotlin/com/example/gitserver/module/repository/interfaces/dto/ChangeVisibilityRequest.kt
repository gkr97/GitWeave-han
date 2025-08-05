package com.example.gitserver.module.repository.interfaces.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class ChangeVisibilityRequest(
    @field:NotBlank(message = "공개 여부는 필수값 입니다.")
    @field:Pattern(
        regexp = "^(public|private)$",
        message = "공개 여부는 public 또는 private만 허용합니다."
    )
    val visibility: String
)