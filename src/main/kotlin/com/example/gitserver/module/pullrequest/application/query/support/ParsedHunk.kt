package com.example.gitserver.module.pullrequest.application.query.support

data class ParsedHunk(
    val header: String,       
    val oldStart: Int,
    val newStart: Int,
    val lines: List<ParsedLine>
)