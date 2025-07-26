package com.example.gitserver.module.repository.interfaces.dto

data class CloneUrlsResponse(
    val https: String,
    val ssh: String,
    val zip: String
)