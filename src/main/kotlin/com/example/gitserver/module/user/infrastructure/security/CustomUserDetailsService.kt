package com.example.gitserver.module.user.infrastructure.security

import com.example.gitserver.module.user.domain.CustomUserDetails
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class CustomUserDetailsService(
    private val userRepository: UserRepository
) : UserDetailsService {
    /**
     * 사용자 이름으로 UserDetails를 로드합니다.
     * @param username 사용자 이름
     * @return UserDetails 객체
     * @throws UsernameNotFoundException 사용자를 찾을 수 없는 경우
     */
    override fun loadUserByUsername(username: String?): UserDetails {
        throw UnsupportedOperationException()
    }
    /**
     * 사용자 ID로 UserDetails를 로드합니다.
     * @param userId 사용자 ID
     * @return UserDetails 객체
     * @throws UsernameNotFoundException 사용자를 찾을 수 없는 경우
     */
    fun loadUserById(userId: Long): UserDetails {
        val user = userRepository.findById(userId)
            .orElseThrow { UsernameNotFoundException("사용자를 찾을 수 없습니다") }
        return CustomUserDetails(user)
    }
}
