package com.example.gitserver.module.user.application.query

import com.example.gitserver.module.user.domain.User
import com.example.gitserver.module.user.exception.AuthException
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import com.example.gitserver.common.cache.RequestCache
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthQueryService(
    private val userRepository: UserRepository,
    private val requestCache: RequestCache,
) {

    @Transactional(readOnly = true)
    fun findUserByEmail(email: String): User? {
        val user = userRepository.findByEmailAndIsDeletedFalse(email)
            ?: throw AuthException(
                code = "USER_NOT_FOUND",
                message = "사용자를 찾을 수 없습니다.",
                status = HttpStatus.UNAUTHORIZED
            )
        return user
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = ["userByIdShort"], key = "#userId", unless = "#result == null")
    fun findUserById(userId: Long): User? {
        val cached = runCatching { requestCache.getUser(userId) }.getOrNull()
        val user = cached ?: userRepository.findByIdAndIsDeletedFalse(userId)
            ?: throw AuthException(
                code = "USER_NOT_FOUND",
                message = "사용자를 찾을 수 없습니다.",
                status = HttpStatus.UNAUTHORIZED
            )
        if (cached == null) runCatching { requestCache.putUser(user) }
        return user
    }
}
