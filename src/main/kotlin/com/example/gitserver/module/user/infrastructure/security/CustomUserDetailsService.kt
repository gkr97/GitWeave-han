package com.example.gitserver.module.user.infrastructure.security

import com.example.gitserver.module.user.domain.CustomUserDetails
import com.example.gitserver.common.cache.RequestCache
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class CustomUserDetailsService(
    private val userRepository: UserRepository,
    private val requestCache: RequestCache,
) : UserDetailsService {
    /**
     * 사용자 이름으로 UserDetails를 로드합니다.
     * @param username 사용자 이름
     * @return UserDetails 객체
     * @throws UsernameNotFoundException 사용자를 찾을 수 없는 경우
     */
    override fun loadUserByUsername(username: String?): CustomUserDetails {
        if (username == null) throw UsernameNotFoundException("username이 null입니다")
        val user = userRepository.findByEmailAndIsDeletedFalse(username)
            ?: throw UsernameNotFoundException("사용자를 찾을 수 없습니다: $username")
        return CustomUserDetails(user)
    }
    /**
     * 사용자 ID로 UserDetails를 로드합니다.
     * @param userId 사용자 ID
     * @return UserDetails 객체
     * @throws UsernameNotFoundException 사용자를 찾을 수 없는 경우
     */
    @Cacheable(cacheNames = ["userDetailsByIdShort"], key = "#userId", unless = "#result == null")
    fun loadUserById(userId: Long): UserDetails {
        val cached = runCatching { requestCache.getUser(userId) }.getOrNull()
        val user = cached ?: userRepository.findById(userId)
            .orElseThrow { UsernameNotFoundException("사용자를 찾을 수 없습니다") }
            .also { runCatching { requestCache.putUser(it) } }
        return CustomUserDetails(user)
    }
}
