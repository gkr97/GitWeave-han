package com.example.gitserver.config

import com.example.gitserver.common.jwt.JwtProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JwtConfig(
    @Value("\${jwt.secret}") private val secret: String,
    @Value("\${jwt.expirationMillis}") private val expirationMillis: Long
) {
    @Bean
    fun jwtProvider() = JwtProvider(secret, expirationMillis)
}
