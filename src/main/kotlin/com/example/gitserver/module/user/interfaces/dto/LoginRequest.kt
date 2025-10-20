package com.example.gitserver.module.user.interfaces.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class LoginRequest(
    @field:NotBlank(message = "이메일은 필수입니다.")
    @field:Email(message = "이메일 형식이 아닙니다.")
    @field:Size(max = 100, message = "이메일은 100자 이내여야 합니다.")
    val email: String,

    @field:NotBlank(message = "비밀번호는 필수 입니다.")
    @field:Size(min = 8, max = 50, message = "비밀번호는 8 ~ 50자 이내여야 합니다.")
    val password: String,

    val remember: Boolean
)
