package com.example.gitserver.module.user.application.query

import com.example.gitserver.module.user.domain.vo.RefreshToken

interface RefreshTokenQuery {
    fun findByUserId(userId: Long): RefreshToken?
}