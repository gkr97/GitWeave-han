package com.example.gitserver.common.pagination

data class Edge<T>(val cursor: String, val node: T)
