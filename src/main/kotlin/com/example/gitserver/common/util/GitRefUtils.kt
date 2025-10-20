package com.example.gitserver.common.util

/**
 * Git Ref 관련 유틸리티
 */
object GitRefUtils {

    fun toFullRef(branch: String): String {
        require(branch.isNotBlank()) { "Branch name cannot be blank" }
        return if (branch.startsWith("refs/heads/")) branch else "refs/heads/$branch"
    }

    fun toFullRefOrNull(branch: String?): String? {
        return branch?.let {
            if (it.startsWith("refs/heads/")) it else "refs/heads/$it"
        }
    }

    fun toShortName(fullRef: String?): String? {
        return fullRef?.removePrefix("refs/heads/")
    }
}
