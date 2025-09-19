package com.example.gitserver.module.pullrequest.interfaces.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreatePullRequestCommentRequest(
    @field:NotBlank @field:Size(max = 10_000)
    val content: String,

    // general | review | inline
    val commentType: String = "general",

    // inline/review 용
    val filePath: String? = null,
    val lineNumber: Int? = null
)