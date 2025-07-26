package com.example.gitserver.module.repository.interfaces.dto

data class ReadmeResponse(
    val exists: Boolean,
    val path: String,
    val blobHash: String? = null,
    val content: String? = null,
    val html: String? = null
)