package com.example.gitserver.common.pagination

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.util.*

object CursorCodec {
    private val mapper = jacksonObjectMapper()

    fun encode(p: CursorPayload): String =
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString(mapper.writeValueAsBytes(p))

    fun decode(cursor: String): CursorPayload {
        require(cursor.isNotBlank()) { "cursor is blank" }
        val bytes = Base64.getUrlDecoder().decode(cursor)
        return mapper.readValue(bytes, CursorPayload::class.java)
    }
}