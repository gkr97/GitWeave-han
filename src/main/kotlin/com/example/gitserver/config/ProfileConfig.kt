package com.example.gitserver.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
class ProfileConfig {
    @Configuration
    @Profile("gitindexer")
    @EnableScheduling
    class GitIndexerScheduling

    @Configuration
    @Profile("gitstorage")
    @EnableScheduling
    class GitStorageScheduling

    @Configuration
    @Profile("!gitindexer & !gitstorage")
    @EnableScheduling
    class DefaultScheduling
}
