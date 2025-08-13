package com.example.gitserver.common.pagination

object PagingValidator {
    fun validate(p: KeysetPaging) {
        val forward = (p.first != null || p.after != null)
        val backward = (p.last != null || p.before != null)
        require(!(forward && backward)) { "Use forward OR backward pagination" }
        require(p.first == null || p.first > 0) { "first must be > 0" }
        require(p.last == null || p.last > 0) { "last must be > 0" }
    }

    fun ensureCursorMatchesSort(cursor: CursorPayload, sort: String, dir: String) {
        require(cursor.sort == sort && cursor.dir == dir) {
            "cursor sort direction mismatch"
        }
    }
}