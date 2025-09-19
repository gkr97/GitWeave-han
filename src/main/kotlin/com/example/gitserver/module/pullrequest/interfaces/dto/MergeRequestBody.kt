package com.example.gitserver.module.pullrequest.interfaces.dto

data class MergeRequestBody(
    val mergeType: String = "merge_commit",
    val message: String? = null
)