package com.example.gitserver.module.repository.infrastructure.persistence

import com.example.gitserver.module.repository.domain.RepositoryBookmark
import com.example.gitserver.module.repository.domain.RepositoryBookmarkId
import org.springframework.data.jpa.repository.JpaRepository

interface BookmarkRepository : JpaRepository<RepositoryBookmark, RepositoryBookmarkId> {
    fun existsByUserIdAndRepositoryId(userId: Long, repositoryId: Long): Boolean
}