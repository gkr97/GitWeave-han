package com.example.gitserver.module.repository.interfaces.dto

import java.time.LocalDateTime

data class RepoDetailResponse (
    val id: Long,
    val name: String,
    val description: String?,
    val visibility: String,
    val createdAt: LocalDateTime,
    val lastUpdatedAt: LocalDateTime?,
    val owner: RepositoryUserResponse,
    val isStarred: Boolean,
    val isOwner: Boolean,
    val isInvited: Boolean,
    val contributors: List<RepositoryUserResponse>,
    val openPrCount: Int,
    val languageStats: List<LanguageStatResponse>,
    val cloneUrls: CloneUrlsResponse,
    val readme: ReadmeResponse,
    val defaultBranch: String,
    val branches: List<BranchResponse>,
    val stats: RepositoryStatsResponse,
    val fileTree: List<TreeNodeResponse>
    )