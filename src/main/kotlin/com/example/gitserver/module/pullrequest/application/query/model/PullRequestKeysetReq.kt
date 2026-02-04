package com.example.gitserver.module.pullrequest.application.query.model

import com.example.gitserver.common.pagination.KeysetPaging
import com.example.gitserver.common.pagination.SortDirection

data class PullRequestKeysetReq(
    val repoId: Long,
    val paging: KeysetPaging,
    val sort: PullRequestSortBy,
    val dir: SortDirection,
    val keyword: String?,
    val status: String?
)
