package com.example.gitserver.module.gitindex.domain.port

interface GitLogPort {
    fun listCommitsBetween(bareGitPath: String, base: String, head: String): List<String>
}