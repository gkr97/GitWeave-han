package com.example.gitserver.module.repository.interfaces.dto

data class FileExplorerResponse(
    val fileTree: List<TreeNodeResponse>,
    val fileContent: FileContentResponse?
)
