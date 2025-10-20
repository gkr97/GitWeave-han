package com.example.gitserver.module.pullrequest.domain

enum class PrMergeType(val code: String) {
    MERGE_COMMIT("merge_commit"),
    SQUASH("squash"),
    REBASE("rebase");

    companion object {
        fun fromCode(code: String): PrMergeType? =
            values().firstOrNull { it.code.equals(code, ignoreCase = true) }
    }
}