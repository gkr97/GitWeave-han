package com.example.gitserver.common.resilience

import mu.KotlinLogging
import org.springframework.dao.DataAccessException
import org.springframework.dao.DeadlockLoserDataAccessException
import org.springframework.dao.QueryTimeoutException
import org.springframework.dao.TransientDataAccessException
import java.util.concurrent.ThreadLocalRandom

/**
 * 데이터베이스 작업에 대한 재시도 유틸리티
 * 일시적 오류(네트워크, 데드락 등)에 대해 재시도
 */
object DatabaseRetryUtils {

    val log = KotlinLogging.logger {}
    /**
     * 데이터베이스 작업을 재시도합니다.
     * 
     * @param maxRetries 최대 재시도 횟수
     * @param baseDelayMs 기본 대기 시간 (ms)
     * @param block 실행할 블록
     * @return 실행 결과
     */
    inline fun <T> retry(
        maxRetries: Int = 3,
        baseDelayMs: Long = 100L,
        block: () -> T
    ): T {
        var attempt = 0
        var lastException: Throwable? = null
        
        while (attempt < maxRetries) {
            try {
                return block()
            } catch (e: TransientDataAccessException) {
                lastException = e
                attempt++
                if (attempt >= maxRetries) {
                    log.warn(e) { "[DB Retry] 최대 재시도 횟수 초과: $maxRetries" }
                    break
                }
                val delay = baseDelayMs * (1L shl (attempt - 1)) + ThreadLocalRandom.current().nextLong(0, 50)
                log.debug { "[DB Retry] 재시도 ${attempt}/$maxRetries, ${delay}ms 후 재시도" }
                Thread.sleep(delay)
            } catch (e: DataAccessException) {
                log.error(e) { "[DB Retry] 재시도 불가능한 오류" }
                throw e
            } catch (e: Exception) {
                throw e
            }
        }
        
        throw lastException ?: IllegalStateException("재시도 실패")
    }
    
    /**
     * 재시도 가능한 예외인지 확인
     */
    fun isRetryable(exception: Throwable): Boolean {
        return when (exception) {
            is TransientDataAccessException -> true
            is QueryTimeoutException -> true
            is DeadlockLoserDataAccessException -> true
            else -> false
        }
    }
}
