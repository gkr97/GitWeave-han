package com.example.gitserver.module.gitindex.shared.domain.dto

import com.example.gitserver.module.gitindex.shared.domain.vo.MergeType
import com.example.gitserver.module.repository.domain.Repository
import java.time.ZoneId

data class MergeRequest(
    val repository: Repository,
    val sourceRef: String,
    val targetRef: String,
    val mergeType: MergeType,
    val authorName: String,
    val authorEmail: String,
    val timeZone: ZoneId = ZoneId.systemDefault(),
    val message: String? = null
)