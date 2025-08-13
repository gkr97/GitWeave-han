package com.example.gitserver.module.repository.application.query.model

import com.example.gitserver.module.repository.interfaces.dto.BranchResponse

data class BranchEdge(
    val cursor: String,
    val node: BranchResponse
)