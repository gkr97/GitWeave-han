package com.example.gitserver.module.pullrequest.interfaces.dto

data class MergePullRequestRequest(
    val mergeType: String? = "merge_commit"
)