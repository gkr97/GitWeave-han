package com.example.gitserver.common.exception

import jakarta.servlet.*
import jakarta.servlet.http.HttpServletRequest
import com.example.gitserver.module.user.domain.CustomUserDetails
import org.slf4j.MDC
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * 필터를 통해 HTTP 요청에 X-Correlation-Id 헤더가 없으면 UUID를 생성하여 MDC에 저장합니다.
 * 이후 로깅 시 이 값을 사용할 수 있습니다.
 */
@Component
class CorrelationIdFilter : Filter {
    override fun doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain) {
        val http = req as HttpServletRequest
        val cid = http.getHeader("X-Correlation-Id") ?: UUID.randomUUID().toString()
        MDC.put("correlationId", cid)
        val principal = SecurityContextHolder.getContext().authentication?.principal
        if (principal is CustomUserDetails) {
            MDC.put("userId", principal.getUserId().toString())
        }
        try { chain.doFilter(req, res) } finally {
            MDC.remove("correlationId")
            MDC.remove("userId")
        }
    }
}
