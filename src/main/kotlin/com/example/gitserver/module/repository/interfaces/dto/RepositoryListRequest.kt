package com.example.gitserver.module.repository.interfaces.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class RepositoryListRequest(
    @field:Min(value = 1, message = "페이지 번호는 1 이상이어야 합니다.")
    val page: Int = 1,

    @field:Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
    val size: Int = 10,

    @field:Pattern(
        regexp = "^(lastUpdatedAt|name|createdAt)$",
        message = "정렬 기준은 lastUpdatedAt, name, createdAt만 허용합니다."
    )
    val sortBy: String = "lastUpdatedAt",

    @field:Pattern(
        regexp = "^(ASC|DESC)$",
        message = "정렬 방향은 ASC 또는 DESC만 허용합니다."
    )
    val sortDirection: String = "DESC",

    @field:Size(max = 100, message = "검색어는 최대 100자까지 허용합니다.")
    val keyword: String? = null
)
