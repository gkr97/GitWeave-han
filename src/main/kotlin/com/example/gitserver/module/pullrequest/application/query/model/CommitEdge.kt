package com.example.gitserver.module.pullrequest.application.query.model

import com.example.gitserver.module.repository.interfaces.dto.CommitResponse

data class CommitEdge(
    val cursor: String,
    val node: CommitResponse
)