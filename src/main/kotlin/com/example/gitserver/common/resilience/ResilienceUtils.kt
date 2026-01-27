package com.example.gitserver.common.resilience

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryRegistry
import mu.KotlinLogging
import java.util.function.Supplier

private val log = KotlinLogging.logger {}

/**
 * Resilience4j 유틸리티
 * Circuit Breaker와 Retry를 조합하여 사용
 */
object ResilienceUtils {

    /**
     * Circuit Breaker + Retry 적용
     */
    fun <T> executeWithResilience(
        circuitBreakerName: String,
        retryName: String,
        circuitBreakerRegistry: CircuitBreakerRegistry,
        retryRegistry: RetryRegistry,
        block: () -> T
    ): T {
        val circuitBreaker = circuitBreakerRegistry.circuitBreaker(circuitBreakerName)
        val retry = retryRegistry.retry(retryName)

        val supplier: Supplier<T> = Supplier { block() }
        val cbSupplier: Supplier<T> =
            CircuitBreaker.decorateSupplier(circuitBreaker, supplier)
        val retrySupplier: Supplier<T> =
            Retry.decorateSupplier(retry, cbSupplier)

        return try {
            retrySupplier.get()
        } catch (e: Exception) {
            log.error(e) {
                "[Resilience] 실패 - circuitBreaker=$circuitBreakerName, retry=$retryName, " +
                        "circuitState=${circuitBreaker.state}"
            }
            throw e
        }
    }

    /**
     * Circuit Breaker만 적용
     */
    fun <T> executeWithCircuitBreaker(
        circuitBreakerName: String,
        circuitBreakerRegistry: CircuitBreakerRegistry,
        block: () -> T
    ): T {
        val circuitBreaker = circuitBreakerRegistry.circuitBreaker(circuitBreakerName)
        return CircuitBreaker
            .decorateSupplier(circuitBreaker, Supplier { block() })
            .get()
    }

    /**
     * Retry만 적용
     */
    fun <T> executeWithRetry(
        retryName: String,
        retryRegistry: RetryRegistry,
        block: () -> T
    ): T {
        val retry = retryRegistry.retry(retryName)
        return Retry
            .decorateSupplier(retry, Supplier { block() })
            .get()
    }
}
