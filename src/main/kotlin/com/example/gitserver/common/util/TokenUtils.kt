package com.example.gitserver.common.util

import java.util.*

object TokenUtils {
    fun generateVerificationToken(): String = UUID.randomUUID().toString()
}