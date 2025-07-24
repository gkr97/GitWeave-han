package com.example.gitserver.module.user.interfaces.rest.dto

import com.example.gitserver.module.user.domain.User


data class UserResponse(
    val id: Long,
    val email: String,
    val name: String?,
) {
    companion object {
        fun from(user: User): UserResponse {
            return UserResponse(
                id = user.id,
                email = user.email,
                name = user.name,
            )
        }
    }
}
