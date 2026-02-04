package com.example.gitserver.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.security.concurrent.DelegatingSecurityContextExecutor
import com.example.gitserver.common.util.LogContext
import java.util.concurrent.Executor

/**
 * 비동기 처리 설정
 */
@Configuration
@EnableAsync
class AsyncConfig {

    @Bean(name = ["virtualThreadTaskExecutor"])
    fun virtualThreadTaskExecutor(): Executor {
        val baseExecutor = Executor { task ->
            Thread.startVirtualThread(LogContext.wrap(task))
        }
        return DelegatingSecurityContextExecutor(baseExecutor)
    }
}
