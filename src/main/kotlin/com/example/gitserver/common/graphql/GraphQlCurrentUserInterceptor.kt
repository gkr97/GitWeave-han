package com.example.gitserver.common.graphql

import com.example.gitserver.module.user.domain.CustomUserDetails
import org.springframework.graphql.server.WebGraphQlInterceptor
import org.springframework.graphql.server.WebGraphQlRequest
import org.springframework.graphql.server.WebGraphQlResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import org.slf4j.MDC

@Component
class GraphQlCurrentUserInterceptor : WebGraphQlInterceptor {

    override fun intercept(
        request: WebGraphQlRequest,
        chain: WebGraphQlInterceptor.Chain
    ): Mono<WebGraphQlResponse> {
        val principal = SecurityContextHolder.getContext().authentication?.principal
        val userId = if (principal is CustomUserDetails) principal.getUserId().toString() else null

        request.configureExecutionInput { _, builder ->
            builder.graphQLContext { ctx ->
                if (principal is CustomUserDetails) {
                    ctx.put("currentUser", principal)
                    ctx.put("currentUserId", principal.getUserId())
                }
            }.build()
        }

        if (userId != null) {
            MDC.put("userId", userId)
        }

        return chain.next(request)
            .doFinally {
                if (userId != null) {
                    MDC.remove("userId")
                }
            }
    }
}
