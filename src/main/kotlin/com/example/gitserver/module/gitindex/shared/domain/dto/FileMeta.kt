package com.example.gitserver.module.gitindex.shared.domain.dto

data class FileMeta(
    val path: String,
    val externalStorageKey: String,
    val isBinary: Boolean,
    val mimeType: String?,
    val size: Long?
)
