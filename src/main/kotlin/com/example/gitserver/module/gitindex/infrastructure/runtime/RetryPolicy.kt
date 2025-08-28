package com.example.gitserver.module.gitindex.infrastructure.runtime

import mu.KotlinLogging
import java.time.Duration
import java.util.concurrent.ThreadLocalRandom


object RetryPolicy {

    val log = KotlinLogging.logger {}

    inline fun <T> retry(
        maxRetries: Int,
        baseDelay: Duration,
        crossinline block: () -> T
    ): T {
        var attempt = 0
        var lastErr: Throwable? = null
        while (attempt < maxRetries) {
            try { return block() } catch (e: Throwable) {
                lastErr = e
                val base = baseDelay.multipliedBy(1L shl attempt).toMillis()
                val jitter = ThreadLocalRandom.current().nextLong(50, 150)
                val delayMs = base + jitter
                log.warn(e) { "[retry] 실패 ${attempt + 1}/$maxRetries, ${delayMs}ms 후 재시도" }
                Thread.sleep(delayMs)
                attempt++
            }
        }
        throw lastErr ?: IllegalStateException("retry 실패")
    }
}
