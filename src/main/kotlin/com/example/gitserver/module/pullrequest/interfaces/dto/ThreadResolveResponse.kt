package com.example.gitserver.module.pullrequest.interfaces.dto

data class ThreadResolveResponse(
    val threadId: Long,
    val resolved: Boolean
)