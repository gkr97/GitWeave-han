package com.example.gitserver.module.user.application.command.service

import com.example.gitserver.module.user.domain.vo.RefreshToken

interface RefreshTokenService {
    fun save(token: RefreshToken)
    fun delete(userId: Long)
}