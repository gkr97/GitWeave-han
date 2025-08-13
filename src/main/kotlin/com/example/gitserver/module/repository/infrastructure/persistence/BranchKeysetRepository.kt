package com.example.gitserver.module.repository.infrastructure.persistence

import com.example.gitserver.module.repository.application.query.model.BranchKeysetReq
import com.example.gitserver.module.repository.application.query.model.BranchRow

interface BranchKeysetRepository {
    fun query(req: BranchKeysetReq): List<BranchRow>
}