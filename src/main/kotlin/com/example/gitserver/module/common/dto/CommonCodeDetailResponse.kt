package com.example.gitserver.module.common.dto

data class CommonCodeDetailResponse(
    val id: Long,
    val code: String,
    val name: String,
    val sortOrder: Int,
    val isActive: Boolean
)