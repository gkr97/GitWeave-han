package com.example.gitserver.common.jwt

import org.springframework.http.ResponseCookie
import java.time.Duration

/**
 * 쿠키 생성 및 삭제를 위한 유틸리티 객체입니다.
 */
object CookieSupport {
    enum class SameSite(val attr: String) { Lax("Lax"), Strict("Strict"), None("None") }

    fun buildHttpOnlyCookie(
        name: String,
        value: String,
        maxAge: Duration,
        sameSite: SameSite = SameSite.Lax,
        secure: Boolean = true,
        path: String = "/",
        domain: String? = null
    ): String {
        val builder = ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(secure)
            .sameSite(sameSite.attr)
            .path(path)
            .maxAge(maxAge)
        domain?.let { builder.domain(it) }
        return builder.build().toString()
    }

    fun deleteCookie(
        name: String,
        sameSite: SameSite = SameSite.Lax,
        secure: Boolean = true,
        path: String = "/",
        domain: String? = null
    ): String = buildHttpOnlyCookie(
        name = name,
        value = "",
        maxAge = Duration.ZERO,
        sameSite = sameSite,
        secure = secure,
        path = path,
        domain = domain
    )
}
