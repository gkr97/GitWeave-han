package com.example.gitserver.common.pagination

object ConnectionBuilder {
    fun <T> forward(rows: List<T>, pageSize: Int, toCursor: (T) -> String): Connection<T> {
        val hasNext = rows.size > pageSize
        val slice = rows.take(pageSize)
        val edges = slice.map { Edge(toCursor(it), it) }
        return Connection(
            edges,
            PageInfoDTO(hasNext, hasPreviousPage = false,
                startCursor = edges.firstOrNull()?.cursor,
                endCursor = edges.lastOrNull()?.cursor)
        )
    }

    fun <T> backward(rowsDesc: List<T>, pageSize: Int, toCursor: (T) -> String): Connection<T> {
        val hasPrev = rowsDesc.size > pageSize
        val slice = rowsDesc.take(pageSize).asReversed()
        val edges = slice.map { Edge(toCursor(it), it) }
        return Connection(
            edges,
            PageInfoDTO(hasNextPage = false, hasPreviousPage = hasPrev,
                startCursor = edges.firstOrNull()?.cursor,
                endCursor = edges.lastOrNull()?.cursor)
        )
    }
}