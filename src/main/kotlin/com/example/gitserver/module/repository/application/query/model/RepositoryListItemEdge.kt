package com.example.gitserver.module.repository.application.query.model

import com.example.gitserver.module.repository.interfaces.dto.RepositoryListItem

data class RepositoryListItemEdge(
    val cursor: String,
    val node: RepositoryListItem
)