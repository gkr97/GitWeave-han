package com.example.gitserver.module.repository.interfaces.dto

data class RepositoryListItem(
    val id: Long,
    val name: String,
    val description: String?,
    val visibility: String,
    val lastUpdatedAt: String,
    val isOwner: Boolean,
    val language: String?,
    val isStarred: Boolean,
    val isInvited: Boolean,
    val ownerInfo: RepositoryUserResponse?
)