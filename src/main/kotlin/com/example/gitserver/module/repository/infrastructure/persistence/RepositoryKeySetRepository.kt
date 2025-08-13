package com.example.gitserver.module.repository.infrastructure.persistence

import com.example.gitserver.module.repository.application.query.model.RepoKeysetReq
import com.example.gitserver.module.repository.application.query.model.RepoRow

interface RepositoryKeySetRepository {
    fun query(req : RepoKeysetReq, limit: Int): List<RepoRow>
}