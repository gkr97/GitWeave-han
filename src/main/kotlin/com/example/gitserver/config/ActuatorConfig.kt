package com.example.gitserver.config

import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest
import org.springframework.boot.actuate.health.HealthEndpoint
import org.springframework.context.annotation.Configuration

/**
 * Spring Actuator 설정
 * - SecurityConfig에서 /actuator/health는 permitAll로 설정됨
 * - 나머지 endpoint는 인증 필요
 */
@Configuration
class ActuatorConfig {
    // Actuator 보안 설정은 SecurityConfig에서 통합 관리
}
