package com.example.gitserver.module.repository.interfaces.dto

data class UserRepositoriesResult(
    val profile: RepositoryUserResponse,
    val repositories: UserRepositoryListPageResponse
)