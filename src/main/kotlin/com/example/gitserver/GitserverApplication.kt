package com.example.gitserver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.security.core.context.SecurityContextHolder

@SpringBootApplication
@EnableScheduling
class GitserverApplication

fun main(args: Array<String>) {
    SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL)
    runApplication<GitserverApplication>(*args)
}
