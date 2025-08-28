package com.example.gitserver.module.gitindex.infrastructure.runtime

import org.springframework.stereotype.Component
import java.util.concurrent.ExecutorService
import java.util.concurrent.Semaphore
import jakarta.annotation.PreDestroy
import java.time.Duration
import java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Component
class ExecutorGateway {
    private val pool: ExecutorService = newVirtualThreadPerTaskExecutor()
    fun submit(permits: Semaphore, block: () -> Unit) {
        permits.acquireUninterruptibly()
        pool.execute {
            try { block() } finally { permits.release() }
        }
    }
    fun barrier(permits: Semaphore, maxPermits: Int, timeout: Duration = Duration.ofMinutes(1)) {
        var acquired = 0
        try {
            val ok = permits.tryAcquire(maxPermits, timeout.toMillis(), TimeUnit.MILLISECONDS)
            if (!ok) throw TimeoutException("barrier 시간 부족: 시간 $timeout")
            acquired = maxPermits
        } finally {
            if (acquired > 0) permits.release(acquired)
        }
    }
    @PreDestroy fun shutdown() { pool.shutdown() }
}
