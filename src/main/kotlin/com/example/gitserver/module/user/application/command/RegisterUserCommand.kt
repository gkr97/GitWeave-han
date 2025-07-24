package com.example.gitserver.module.user.application.command

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class RegisterUserCommand(
    @field:NotBlank(message = "{NotBlank.user.email}")
    @field:Email(message = "{Email.user.email}")
    @field:Size(max = 100, message = "이메일은 최대 100자까지 허용합니다.")
    val email: String,

    @field:NotBlank(message = "{NotBlank.user.password}")
    @field:Size(min = 8, max = 50, message = "비밀번호는 8~50자여야 합니다.")
    @field:Pattern(
        regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#\$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).{8,50}\$",
        message = "비밀번호는 영문, 숫자, 특수문자를 포함해야 합니다."
    )
    val password: String,

    @field:Size(max = 30, message = "이름은 최대 30자까지 허용합니다.")
    val name: String? = null
)
