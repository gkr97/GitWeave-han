package com.example.gitserver.module.repository.interfaces.dto

data class MyRepositoriesResponse(
    val profile: RepositoryUserResponse,
    val repositories:RepositoryListPageResponse,
)