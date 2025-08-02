package com.example.gitserver.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.security.concurrent.DelegatingSecurityContextExecutor
import java.util.concurrent.Executor

@Configuration
@EnableAsync
class AsyncConfig {

    @Bean(name = ["virtualThreadTaskExecutor"])
    fun virtualThreadTaskExecutor(): Executor {
        val baseExecutor = Executor { task ->
            Thread.startVirtualThread(task)
        }
        return DelegatingSecurityContextExecutor(baseExecutor)
    }
}