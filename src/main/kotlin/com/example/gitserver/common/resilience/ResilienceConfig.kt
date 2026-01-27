package com.example.gitserver.common.resilience

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

/**
 * Resilience4j 설정
 * - Circuit Breaker: 외부 서비스 장애 시 빠른 실패
 * - Retry: 일시적 오류에 대한 재시도
 */
@Configuration
class ResilienceConfig {

    @Bean
    fun circuitBreakerRegistry(): CircuitBreakerRegistry {
        val config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50f) // 50% 실패율 초과 시 Circuit Open
            .waitDurationInOpenState(Duration.ofSeconds(30)) // 30초 후 Half-Open
            .slidingWindowSize(10) // 최근 10개 요청 기준
            .minimumNumberOfCalls(5) // 최소 5개 호출 후 통계 계산
            .permittedNumberOfCallsInHalfOpenState(3) // Half-Open 상태에서 3개 호출 허용
            .build()

        return CircuitBreakerRegistry.of(config)
    }

    @Bean
    fun retryRegistry(): RetryRegistry {
        val config = RetryConfig.custom<Any>()
            .maxAttempts(3) // 최대 3회 재시도
            .waitDuration(Duration.ofMillis(200)) // 초기 대기 시간 200ms
            .retryExceptions(Exception::class.java)
            .build()

        return RetryRegistry.of(config)
    }
}
