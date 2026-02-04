package com.example.gitserver.module.gitindex.shared.domain.dto

data class TreeItem(
    val path: String,
    val fileHash: String?
)