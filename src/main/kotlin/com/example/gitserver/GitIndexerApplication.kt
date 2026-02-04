package com.example.gitserver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class GitIndexerApplication

fun main(args: Array<String>) {
    runApplication<GitIndexerApplication>(*args)
}
