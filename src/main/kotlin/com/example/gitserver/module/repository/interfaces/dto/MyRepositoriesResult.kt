package com.example.gitserver.module.repository.interfaces.dto

data class MyRepositoriesResult(
    val profile: RepositoryUserResponse,
    val repositories:RepositoryListPageResponse,
)