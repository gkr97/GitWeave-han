package com.example.gitserver.common.exception

import org.springframework.graphql.server.WebGraphQlInterceptor
import org.springframework.graphql.server.WebGraphQlRequest
import org.springframework.graphql.server.WebGraphQlResponse
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.time.Instant
import org.slf4j.MDC

/**
 * GraphQL 요청에 대한 메타데이터를 추가하는 인터셉터입니다.
 * - 성공 여부, 상태 코드, 메시지, 타임스탬프, 상관 ID 등을 포함합니다.
 */
@Component
class GraphQlMetaInterceptor : WebGraphQlInterceptor {

    override fun intercept(
        request: WebGraphQlRequest,
        chain: WebGraphQlInterceptor.Chain
    ): Mono<WebGraphQlResponse> {
        val correlationId = request.headers.getFirst("X-Correlation-Id")
            ?: MDC.get("correlationId")

        return chain.next(request).map { resp ->
            val success = resp.errors.isEmpty()
            val defaultMessage = if (success) "OK" else "FAILED"
            val defaultCode = if (success) 200 else 400

            resp.transform { builder ->
                val exts = LinkedHashMap<Any, Any>()
                resp.extensions
                    ?.filterValues { it != null }
                    ?.forEach { (k, v) -> exts[k] = v as Any }

                val meta = mutableMapOf<String, Any>(
                    "success" to success,
                    "code" to defaultCode,
                    "message" to defaultMessage,
                    "timestamp" to Instant.now().toString()
                )
                if (correlationId != null) meta["correlationId"] = correlationId

                exts["meta"] = meta

                builder.extensions(exts)
            }
        }
    }
}
