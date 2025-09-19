package com.example.gitserver.module.pullrequest.application.query.model

data class DiffLine(
    val type: DiffLineType,
    val oldLine: Int?,
    val newLine: Int?,
    val content: String,
    val position: Int,
    val anchor: String,
    val commentCount: Int
)