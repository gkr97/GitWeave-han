package com.example.gitserver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class GitserverApplication

fun main(args: Array<String>) {
    runApplication<GitserverApplication>(*args)
}
