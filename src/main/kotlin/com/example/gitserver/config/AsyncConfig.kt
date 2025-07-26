package com.example.gitserver.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import java.util.concurrent.Executor

@Configuration
@EnableAsync
class AsyncConfig {

    @Bean(name = ["virtualThreadTaskExecutor"])
    fun virtualThreadTaskExecutor(): Executor {
        return Executor { task ->
            Thread.startVirtualThread(task)
        }
    }
}