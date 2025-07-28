package com.example.gitserver.config

import com.example.gitserver.common.jwt.GitPatAuthenticationFilter
import com.example.gitserver.common.jwt.JwtAuthenticationFilter
import com.example.gitserver.common.jwt.JwtProvider
import com.example.gitserver.module.common.service.CommonCodeCacheService
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.user.infrastructure.persistence.PersonalAccessTokenRepository
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import com.example.gitserver.module.user.infrastructure.security.CustomUserDetailsService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import jakarta.servlet.http.HttpServletResponse

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val userRepository: UserRepository,
    private val patRepository: PersonalAccessTokenRepository,
    private val repositoryRepository: RepositoryRepository,
    private val collaboratorRepository: CollaboratorRepository,
    private val commonCodeCacheService: CommonCodeCacheService,
    private val jwtProvider: JwtProvider,
    private val userDetailsService: CustomUserDetailsService
) {

    @Bean
    fun gitPatAuthenticationFilter(): GitPatAuthenticationFilter {
        return GitPatAuthenticationFilter(
            userRepository, patRepository, repositoryRepository, collaboratorRepository, commonCodeCacheService
        )
    }

    @Bean
    fun jwtAuthenticationFilter(): JwtAuthenticationFilter {
        return JwtAuthenticationFilter(
            jwtProvider, userDetailsService
        )
    }

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        "/api/v1/auth/**",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/h2-console/**",
                        "/graphql",
                        "/graphiql/**",
                        "/vendor/**",
                        "/static/**",
                        "/webjars/**",
                        "/{ownerId}/{repo}.git/**",
                    ).permitAll()
                    .anyRequest().authenticated()
            }
            .exceptionHandling { exceptionHandling ->
                exceptionHandling
                    .authenticationEntryPoint { request, response, authException ->
                        response.setHeader("WWW-Authenticate", "Basic realm=\"Git\"")
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, authException.message)
                    }
            }
            .addFilterBefore(gitPatAuthenticationFilter(), UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }
}
