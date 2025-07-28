package com.example.gitserver.module.user.application.service

import com.example.gitserver.module.user.PersonalAccessToken
import com.example.gitserver.module.user.exception.UserNotFoundException
import com.example.gitserver.module.user.infrastructure.persistence.PersonalAccessTokenRepository
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*

@Service
class PatService(
    private val personalAccessTokenRepository: PersonalAccessTokenRepository,
    private val userRepository: UserRepository
) {

    fun issuePat(userId: Long, rawToken: String, description: String? = null, expiresAt: LocalDateTime? = null): PersonalAccessToken {
        val tokenHash = hashPat(rawToken)
        val pat = PersonalAccessToken(
            userId = userId,
            tokenHash = tokenHash,
            description = description,
            expiresAt = expiresAt
        )
        return personalAccessTokenRepository.save(pat)
    }

    fun validatePat(userId: Long, rawToken: String): Boolean {
        val tokenHash = hashPat(rawToken)
        val pat = personalAccessTokenRepository.findByUserIdAndTokenHashAndIsActiveTrue(userId, tokenHash)
        return pat != null && (pat.expiresAt == null || pat.expiresAt!!.isAfter(LocalDateTime.now()))
    }

    fun deactivatePat(patId: Long, userId: Long) {
        val user = userRepository.findById(userId).orElseThrow { UserNotFoundException(userId) }
        val pat = personalAccessTokenRepository.findById(patId).orElseThrow()
        pat.isActive = false
        personalAccessTokenRepository.save(pat)
    }

    fun resolveUserIdByAuthHeader(authorization: String?): Long? {
        val isBasic = authorization?.startsWith("Basic ") == true
        val base64Credentials = authorization?.substringAfter("Basic ")?.trim()
        if (isBasic && base64Credentials != null) {
            val credentials = String(Base64.getDecoder().decode(base64Credentials))
            val parts = credentials.split(":", limit = 2)
            if (parts.size == 2) {
                val username = parts[0]
                val pat = parts[1]
                val user = userRepository.findByEmail(username)
                if (user != null && validatePat(user.id, pat)) {
                    return user.id
                }
            }
        }
        return null
    }

    private fun hashPat(rawToken: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(rawToken.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
