package com.example.gitserver.module.repository.interfaces.dto

data class LanguageStatResponse(
    val extension: String,
    val language: String,
    val count: Int,
    val ratio: Float
)