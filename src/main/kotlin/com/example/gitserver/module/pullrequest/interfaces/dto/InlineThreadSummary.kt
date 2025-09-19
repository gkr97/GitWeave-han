package com.example.gitserver.module.pullrequest.interfaces.dto

data class InlineThreadSummary(
    val anchor: String,
    val threadId: Long,
    val totalComments: Int,
    val resolved: Boolean
)

