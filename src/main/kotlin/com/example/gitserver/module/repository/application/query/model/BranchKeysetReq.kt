package com.example.gitserver.module.repository.application.query.model

import com.example.gitserver.common.pagination.KeysetPaging
import com.example.gitserver.common.pagination.SortDirection

data class BranchKeysetReq(
    val repoId: Long,
    val paging: KeysetPaging,
    val sort: BranchSortBy,
    val dir: SortDirection,
    val keyword: String?,
    val onlyMine: Boolean,
    val currentUserId: Long?
)