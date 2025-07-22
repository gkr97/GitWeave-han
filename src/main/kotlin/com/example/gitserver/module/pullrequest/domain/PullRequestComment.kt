package com.example.gitserver.module.pullrequest.domain

import java.time.LocalDateTime
import jakarta.persistence.*
import com.example.gitserver.module.user.domain.User

@Entity
@Table(name = "pull_request_comment")
data class PullRequestComment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pull_request_id", nullable = false)
    val pullRequest: PullRequest,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    val author: User,

    @Column(columnDefinition = "TEXT", nullable = false)
    var content: String,

    @Column(name = "file_path", columnDefinition = "TEXT")
    var filePath: String? = null,

    @Column(name = "line_number")
    var lineNumber: Int? = null,

    @Column(name = "comment_type", nullable = false, length = 20)
    var commentType: String = "general",

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) 