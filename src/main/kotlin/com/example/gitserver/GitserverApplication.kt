package com.example.gitserver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class GitserverApplication

fun main(args: Array<String>) {
    runApplication<GitserverApplication>(*args)
}
