package com.example.gitserver.module.user.application.service

import com.example.gitserver.module.user.domain.PersonalAccessToken
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

    /**
     * 사용자에게 개인 액세스 토큰(PAT)을 발급합니다.
     *
     * @param userId 사용자 ID
     * @param rawToken 발급할 토큰의 원본 문자열
     * @param description 토큰 설명 (선택적)
     * @param expiresAt 토큰 만료 시간 (선택적)
     * @return 발급된 PersonalAccessToken 객체
     */
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

    /**
     * 사용자 ID와 토큰 문자열을 사용하여 PAT를 검증합니다.
     *
     * @param userId 사용자 ID
     * @param rawToken 검증할 토큰의 원본 문자열
     * @return 유효한 PAT가 존재하면 true, 그렇지 않으면 false
     */
    fun validatePat(userId: Long, rawToken: String): Boolean {
        val tokenHash = hashPat(rawToken)
        val pat = personalAccessTokenRepository.findByUserIdAndTokenHashAndIsActiveTrue(userId, tokenHash)
        return pat != null && (pat.expiresAt == null || pat.expiresAt!!.isAfter(LocalDateTime.now()))
    }

    /**
     * 사용자 ID로 활성화된 PAT 목록을 조회합니다.
     *
     * @param userId 사용자 ID
     * @return 활성화된 PAT 목록
     */
    fun deactivatePat(patId: Long, userId: Long) {
        val user = userRepository.findById(userId).orElseThrow { UserNotFoundException(userId) }
        val pat = personalAccessTokenRepository.findById(patId).orElseThrow()
        pat.isActive = false
        personalAccessTokenRepository.save(pat)
    }

    /**
     * Authorization 헤더에서 사용자 ID를 추출합니다.
     *
     * @param authorization Authorization 헤더 값
     * @return 사용자 ID 또는 null
     */
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

    /**
     * 사용자 ID로 활성화된 PAT 목록을 조회합니다.
     *
     * @param userId 사용자 ID
     * @return 활성화된 PAT 목록
     */
    private fun hashPat(rawToken: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(rawToken.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
