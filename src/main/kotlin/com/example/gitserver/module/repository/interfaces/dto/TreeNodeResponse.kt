package com.example.gitserver.module.repository.interfaces.dto

data class TreeNodeResponse(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long? = null
)