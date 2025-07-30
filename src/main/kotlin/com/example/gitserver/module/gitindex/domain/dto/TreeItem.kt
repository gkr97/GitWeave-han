package com.example.gitserver.module.gitindex.domain.dto

data class TreeItem(
    val path: String,
    val fileHash: String?
)