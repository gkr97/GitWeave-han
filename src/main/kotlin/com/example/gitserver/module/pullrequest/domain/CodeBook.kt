package com.example.gitserver.module.pullrequest.domain

import com.example.gitserver.module.gitindex.domain.vo.MergeType

enum class PrStatus { OPEN, CLOSED, MERGED }
enum class PrReviewStatus { PENDING, APPROVED, CHANGES_REQUESTED, DISMISSED }

interface CodeBook {
    fun prStatusId(status: PrStatus): Long
    fun prReviewStatusId(status: PrReviewStatus): Long
    fun prMergeTypeId(type: PrMergeType): Long
    fun toGitMergeType(type: PrMergeType): MergeType
}