package com.example.gitserver.module.user.interfaces.dto
import jakarta.validation.constraints.NotBlank

data class UpdateNameRequest(
    @field:NotBlank(message = "이름은 공백일 수 없습니다.")
    val name: String
)