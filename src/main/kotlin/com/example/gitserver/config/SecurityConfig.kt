package com.example.gitserver.config

import com.example.gitserver.common.jwt.GitPatAuthenticationFilter
import com.example.gitserver.common.jwt.JwtAuthenticationFilter
import com.example.gitserver.common.jwt.JwtProvider
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
import org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtProvider: JwtProvider,
    private val userDetailsService: CustomUserDetailsService,

    private val gitPatAuthenticationFilter: GitPatAuthenticationFilter
) {

    @Bean
    fun jwtAuthenticationFilter(): JwtAuthenticationFilter {
        return JwtAuthenticationFilter(jwtProvider, userDetailsService)
    }

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { }
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
                        "/api/v1/repositories/*/download"
                    ).permitAll()
                    .anyRequest().authenticated()
            }
            .exceptionHandling { exceptionHandling ->
                exceptionHandling
                    .authenticationEntryPoint { request, response, authException ->
                        val uri = request.requestURI
                        if (uri.endsWith(".git") || uri.contains(".git/")) {
                            response.setHeader("WWW-Authenticate", "Basic realm=\"Git\"")
                        }
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, authException.message)
                    }
            }
            .addFilterBefore(WebAsyncManagerIntegrationFilter(), UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(gitPatAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
