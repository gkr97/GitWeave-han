package com.example.gitserver.module.gitindex.domain.port

interface ReadmeQueryRepository {
    fun findReadmeBlobInfo(repoId: Long, commitHash: String): Pair<String, String?>?
}