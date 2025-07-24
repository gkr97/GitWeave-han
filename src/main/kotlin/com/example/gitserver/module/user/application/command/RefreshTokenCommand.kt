package com.example.gitserver.module.user.application.command

import com.example.gitserver.module.user.domain.vo.RefreshToken

interface RefreshTokenCommand {
    fun save(token: RefreshToken)
    fun delete(userId: Long)
}