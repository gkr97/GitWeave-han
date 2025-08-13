package com.example.gitserver.module.repository.application.query.model

import com.example.gitserver.common.pagination.KeysetPaging
import com.example.gitserver.common.pagination.SortDirection

data class RepoKeysetReq(
    val paging: KeysetPaging,
    val sort: RepoSortBy,
    val dir: SortDirection,
    val keyword: String?,
    val idFilter: List<Long>,
)
