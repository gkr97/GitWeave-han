package com.example.gitserver.module.user.application.query

import com.example.gitserver.module.user.domain.User
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import org.springframework.stereotype.Service

@Service
class UserQueryService(
    private val userRepository: UserRepository
) {

    fun findByNickname(nickname: String): User? {
        return userRepository.findByName(nickname)
    }
}
