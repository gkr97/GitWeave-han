package com.example.gitserver.common.pagination

data class CursorPayload(
    val v: Int = 1,
    val sort: String,
    val dir: String,
    val k: Map<String, String>
)
