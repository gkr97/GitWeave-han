package com.example.gitserver.module.user.domain

import com.example.gitserver.module.user.domain.User
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

/**
 * CustomUserDetails는 Spring Security에서 사용자 정보를 나타내는 클래스
 * 사용자의 이메일을 사용자 이름으로 사용하고, 비밀번호 해시를 반환합니다.
 */
class CustomUserDetails(
    private val user: User
) : UserDetails {
    override fun getAuthorities(): Collection<GrantedAuthority> = listOf()
    override fun getPassword(): String = user.passwordHash
    override fun getUsername(): String = user.email
    override fun isAccountNonExpired() = true
    override fun isAccountNonLocked() = true
    override fun isCredentialsNonExpired() = true
    override fun isEnabled() = user.isActive
    fun getUserId() = user.id
    fun getUser() = user
}
