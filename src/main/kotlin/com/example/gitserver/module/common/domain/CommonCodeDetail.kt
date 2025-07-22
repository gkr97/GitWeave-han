package com.example.gitserver.module.common.domain

import java.time.LocalDateTime
import jakarta.persistence.*

@Entity
@Table(name = "common_code_detail")
data class CommonCodeDetail(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "code_group_id", nullable = false)
    val codeGroup: CommonCode,

    @Column(nullable = false, length = 50)
    var code: String,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) 