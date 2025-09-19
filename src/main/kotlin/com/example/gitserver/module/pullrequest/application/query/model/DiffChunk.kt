package com.example.gitserver.module.pullrequest.application.query.model

import com.example.gitserver.module.pullrequest.application.query.model.DiffLine

data class DiffChunk(
    val hunkIndex: Int,
    val header: String,
    val oldStart: Int,
    val newStart: Int,
    val lines: List<DiffLine>
)