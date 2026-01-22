package com.example.gitserver.module.search.domain

data class RepositoryDoc(
    val id: Long,
    val owner_id: Long,
    val name: String,
    val description: String?,
    val topics: List<String> = emptyList(),
    val language: String?,
    val default_branch: String?,
    val stars: Int = 0,
    val forks: Int = 0,
    val watchers: Int = 0,
    val issues: Int = 0,
    val pull_requests: Int = 0,
    val last_commit_at: String?,
    val created_at: String,
    val updated_at: String?
)