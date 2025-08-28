package com.example.gitserver.module.gitindex.domain.port

import com.example.gitserver.module.gitindex.domain.Commit

interface CommitRepository {
    fun save(commit: Commit)
}