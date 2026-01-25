package com.example.gitserver.module.user.application.query

import com.example.gitserver.common.jwt.JwtProvider
import com.example.gitserver.module.user.application.command.service.AuthCommandService
import com.example.gitserver.module.user.application.command.service.RefreshTokenService
import com.example.gitserver.module.user.domain.User
import com.example.gitserver.module.user.domain.vo.RefreshToken
import com.example.gitserver.module.user.exception.AuthException
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

@Service
class AuthQueryService(
    private val userRepository: UserRepository
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
    fun findUserById(userId: Long): User? {
        val user = userRepository.findByIdAndIsDeletedFalse(userId)
            ?: throw AuthException(
                code = "USER_NOT_FOUND",
                message = "사용자를 찾을 수 없습니다.",
                status = HttpStatus.UNAUTHORIZED
            )
        return user
    }
}
