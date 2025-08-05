package com.example.gitserver.module.user.interfaces.dto

import com.example.gitserver.module.user.domain.User

data class UserProfileResponse(
    val id: Long,
    val email: String,
    val name: String?,
    val profileImageUrl: String?
) {
    companion object {
        fun from(user: User): UserProfileResponse {
            return UserProfileResponse(
                id = user.id,
                email = user.email,
                name = user.name,
                profileImageUrl = user.profileImageUrl
            )
        }
    }
}
