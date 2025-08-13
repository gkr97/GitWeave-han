package com.example.gitserver.common.pagination

data class KeysetPaging(
    val first: Int? = null,
    val after: String? = null,
    val last: Int? = null,
    val before: String? = null
) {
    val isForward: Boolean get() = first != null || after != null
    val pageSize: Int get() = (first ?: last ?: 20).coerceIn(1, 100)
}


